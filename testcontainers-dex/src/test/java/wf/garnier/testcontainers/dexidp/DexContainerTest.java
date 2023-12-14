package wf.garnier.testcontainers.dexidp;

import java.io.IOException;
import java.net.URISyntaxException;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import wf.garnier.testcontainers.dexidp.utils.Oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class DexContainerTest {
    static DexContainer container;

    static DexContainer containerWithClients;


    static DexContainer.Client firstClient =
            new DexContainer.Client("client-1", "client-1-secret", "https://example.com/authorized");

    static DexContainer.Client secondClient =
            new DexContainer.Client("client-2", "client-2-secret", "https://example.com/authorized");

    @BeforeAll
    static void beforeAll() {
        containerWithClients = getDefaultContainer()
                .withClient(firstClient)
                .withClient(secondClient);
        containerWithClients.start();

        container = getDefaultContainer();
        container.start();
    }

    @AfterAll
    static void afterAll() {
        containerWithClients.stop();
        container.stop();

    }

    @Test
    void boots() {
        assertThat(container.isRunning()).isTrue();
    }

    @Test
    void servesOpenidConfiguration() throws IOException, InterruptedException {
        var configuration = Oidc.getConfiguration(container.getIssuerUri());
        assertThat(configuration.issuer()).isEqualTo(container.getIssuerUri());
    }

    @Test
    void runsWithOlderVersion() throws IOException, InterruptedException {
        try (var container = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag("v2.36.0"))) {
            container.start();
            var configuration = Oidc.getConfiguration(container.getIssuerUri());
            assertThat(configuration.issuer()).isEqualTo(container.getIssuerUri());
        }
    }

    @Test
    void issuesToken() throws IOException, InterruptedException, URISyntaxException {
        container.start();
        var configuration = Oidc.getConfiguration(container.getIssuerUri());
        var client = container.getClient();
        var user = container.getUser();

        var token = Oidc.obtainToken(configuration, client, user);

        assertThat(container.getClients()).containsExactly(client);
        assertThat(token.idTokenClaims())
                .containsEntry("iss", container.getIssuerUri())
                .containsEntry("aud", client.clientId())
                .containsEntry("name", user.username())
                .containsEntry("email", user.email());
        // The access token contains email, name, etc; but those are not
        // strictly part of the OAuth2 or JWT spec, so we don't test against this.
        assertThat(token.accessTokenClaims())
                .containsEntry("iss", container.getIssuerUri())
                .containsEntry("aud", client.clientId());
    }

    @Test
    void issuerUriOnlyAvailableAfterStartup() {
        try (var container = getDefaultContainer()) {
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(container::getIssuerUri)
                    .withMessage("Issuer URI can only be obtained after the container has started.");
        }
    }

    @Nested
    class Clients {

        @Test
        void multipleClients() throws IOException, InterruptedException, URISyntaxException {
            var configuration = Oidc.getConfiguration(containerWithClients.getIssuerUri());
            var user = containerWithClients.getUser();

            assertThat(containerWithClients.getClients())
                    .hasSize(2)
                    .containsExactly(firstClient, secondClient);
            assertThat(containerWithClients.getClient()).isEqualTo(firstClient);
            assertThat(containerWithClients.getClient("client-1")).isEqualTo(firstClient);
            assertThat(containerWithClients.getClient("client-2")).isEqualTo(secondClient);

            var responseFirst = Oidc.obtainToken(configuration, firstClient, user);
            assertThat(responseFirst.idTokenClaims().get("aud")).isEqualTo("client-1");
            assertThat(responseFirst.accessTokenClaims().get("aud")).isEqualTo("client-1");
            var responseSecond = Oidc.obtainToken(configuration, secondClient, user);
            assertThat(responseSecond.idTokenClaims().get("aud")).isEqualTo("client-2");
            assertThat(responseSecond.accessTokenClaims().get("aud")).isEqualTo("client-2");
        }

        @Test
        void immutableClients() {
            var testClient = new DexContainer.Client("test-client", "test-secret", "https://example.com/authorized");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> containerWithClients.getClients().add(testClient));
            containerWithClients.start();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> containerWithClients.getClients().add(testClient));
        }

        @Test
        void registerClientAfterStart() throws IOException, InterruptedException, URISyntaxException {
            var testClient = new DexContainer.Client("test-client", "test-secret", "https://example.com/authorized");
            var defaultClient = container.getClient();
            container.withClient(testClient);

            var configuration = Oidc.getConfiguration(container.getIssuerUri());
            var user = container.getUser();

            assertThat(container.getClients())
                    .hasSize(2)
                    .containsExactly(defaultClient, testClient);
            assertThat(container.getClient()).isEqualTo(defaultClient);
            assertThat(container.getClient("test-client")).isEqualTo(testClient);

            var responseTest = Oidc.obtainToken(configuration, testClient, user);
            assertThat(responseTest.idTokenClaims().get("aud")).isEqualTo("test-client");
            assertThat(responseTest.accessTokenClaims().get("aud")).isEqualTo("test-client");
        }

        @Test
        void removeClient() throws IOException, InterruptedException {
            var configuration = Oidc.getConfiguration(containerWithClients.getIssuerUri());
            var user = containerWithClients.getUser();

            var removed = containerWithClients.removeClient(firstClient.clientId());

            assertThat(removed).isEqualTo(firstClient);
            assertThat(containerWithClients.getClients())
                    .hasSize(1)
                    .containsExactly(secondClient);
            assertThatExceptionOfType(Oidc.OidcException.class)
                    .isThrownBy(() -> Oidc.obtainToken(configuration, firstClient, user));
            assertThatNoException()
                    .isThrownBy(() -> Oidc.obtainToken(configuration, secondClient, user));
        }

        @Test
        void removeDefaultClient() throws IOException, InterruptedException, URISyntaxException {
            var defaultClient = container.getClient();

            var configuration = Oidc.getConfiguration(container.getIssuerUri());
            var user = container.getUser();

            var responseDefault = Oidc.obtainToken(configuration, defaultClient, user);
            assertThat(responseDefault.idTokenClaims().get("aud")).isEqualTo(defaultClient.clientId());

            var removed = container.removeClient(defaultClient.clientId());
            assertThat(removed).isEqualTo(defaultClient);
            assertThatExceptionOfType(Oidc.OidcException.class)
                    .isThrownBy(() -> Oidc.obtainToken(configuration, defaultClient, user));
        }

        @Test
        void removeClientBeforeStart() {
            var testClient = new DexContainer.Client("test-client", "test-secret", "https://example.com/authorized");
            try (var container = getDefaultContainer()) {
                container.withClient(testClient);
                assertThat(container.removeClient("test-client")).isEqualTo(testClient);
            }
        }

        @Test
        void removeNonExistingClientBeforeStart() {
            try (var container = getDefaultContainer()) {
                assertThat(container.removeClient("this-client-does-not-exist")).isNull();
            }
        }

        @Test
        void removeNonExistingClientAfterStart() {
            assertThat(containerWithClients.removeClient("this-client-does-not-exist")).isNull();
        }
    }

    @Nested
    class Users {
        @Test
        void multipleUsers() throws IOException, InterruptedException, URISyntaxException {
            var alice = new DexContainer.User("alice", "alice@example.com", "alice-password");
            var bob = new DexContainer.User("bob", "bob@example.com", "bob-password");
            try (var container = getDefaultContainer()) {
                container
                        .withUser(alice)
                        .withUser(bob)
                        .start();
                var client = container.getClient();
                var configuration = Oidc.getConfiguration(container.getIssuerUri());

                assertThat(container.getUsers())
                        .hasSize(2)
                        .containsExactly(alice, bob);
                assertThat(container.getUser()).isEqualTo(alice);

                var aliceIdToken = Oidc.obtainToken(configuration, client, alice).idTokenClaims();
                var bobIdToken = Oidc.obtainToken(configuration, client, bob).idTokenClaims();

                assertThat(aliceIdToken)
                        .containsEntry("name", "alice")
                        .containsEntry("email", "alice@example.com");

                assertThat(bobIdToken)
                        .containsEntry("name", "bob")
                        .containsEntry("email", "bob@example.com");

                assertThat(aliceIdToken.get("sub")).isNotEqualTo(bobIdToken.get("sub"));
            }
        }

        @Test
        void mustRegisterUsersBeforeStart() {
            var user = new DexContainer.User("x", "x", "x");
            try (var container = getDefaultContainer()) {
                container.start();
                var defaultUser = container.getUser();
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(() -> container.withUser(user))
                        .withMessage("users cannot be added after the container is started");

                assertThat(container.getUser()).isEqualTo(defaultUser);
            }
        }

        @Test
        void mustStartBeforeGettingUser() {
            try (var container = getDefaultContainer()) {
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getUser)
                        .withMessage("must start the container before accessing the users");
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getUsers)
                        .withMessage("must start the container before accessing the users");
            }
        }

    }

    @NotNull
    private static DexContainer getDefaultContainer() {
        return new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG));
    }

}
