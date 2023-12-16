package wf.garnier.testcontainers.dexidp;

import java.io.IOException;
import java.net.URISyntaxException;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import wf.garnier.testcontainers.dexidp.utils.Oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class DexContainerTest {
    static DexContainer defaultContainer;

    static DexContainer preconfiguredContainer;

    static DexContainer.Client firstClient =
            new DexContainer.Client("client-1", "client-1-secret", "https://example.com/authorized");

    static DexContainer.Client secondClient =
            new DexContainer.Client("client-2", "client-2-secret", "https://example.com/authorized");

    static DexContainer.User alice =
            new DexContainer.User("alice", "alice@example.com", "alice-password");

    static DexContainer.User bob =
            new DexContainer.User("bob", "bob@example.com", "bob-password");

    @BeforeAll
    static void beforeAll() {
        preconfiguredContainer = getDefaultContainer()
                .withUser(alice)
                .withUser(bob);
        preconfiguredContainer.start();

        defaultContainer = getDefaultContainer();
        defaultContainer.start();
    }

    @BeforeEach
    void setUp() {
        // Clean up pre-configured container
        preconfiguredContainer.getClients()
                .stream()
                .map(DexContainer.Client::clientId)
                .forEach(preconfiguredContainer::removeClient);
        preconfiguredContainer
                .withClient(firstClient)
                .withClient(secondClient);
    }

    @AfterAll
    static void afterAll() {
        preconfiguredContainer.stop();
        defaultContainer.stop();
    }

    @Test
    void boots() {
        assertThat(defaultContainer.isRunning()).isTrue();
    }

    @Test
    void servesOpenidConfiguration() throws IOException, InterruptedException {
        var configuration = Oidc.getConfiguration(defaultContainer.getIssuerUri());
        assertThat(configuration.issuer()).isEqualTo(defaultContainer.getIssuerUri());
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
        defaultContainer.start();
        var configuration = Oidc.getConfiguration(defaultContainer.getIssuerUri());
        var client = defaultContainer.getClient();
        var user = defaultContainer.getUser();

        var token = Oidc.obtainToken(configuration, client, user);

        assertThat(defaultContainer.getClients()).containsExactly(client);
        assertThat(token.idTokenClaims())
                .containsEntry("iss", defaultContainer.getIssuerUri())
                .containsEntry("aud", client.clientId())
                .containsEntry("name", user.username())
                .containsEntry("email", user.email());
        // The access token contains email, name, etc; but those are not
        // strictly part of the OAuth2 or JWT spec, so we don't test against this.
        assertThat(token.accessTokenClaims())
                .containsEntry("iss", defaultContainer.getIssuerUri())
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
            var configuration = Oidc.getConfiguration(preconfiguredContainer.getIssuerUri());
            var user = preconfiguredContainer.getUser();

            assertThat(preconfiguredContainer.getClients())
                    .hasSize(2)
                    .containsExactly(firstClient, secondClient);
            assertThat(preconfiguredContainer.getClient()).isEqualTo(firstClient);
            assertThat(preconfiguredContainer.getClient("client-1")).isEqualTo(firstClient);
            assertThat(preconfiguredContainer.getClient("client-2")).isEqualTo(secondClient);

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
                    .isThrownBy(() -> preconfiguredContainer.getClients().add(testClient));
            preconfiguredContainer.start();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> preconfiguredContainer.getClients().add(testClient));
        }

        @Test
        void registerClientAfterStart() throws IOException, InterruptedException, URISyntaxException {
            var testClient = new DexContainer.Client("test-client", "test-secret", "https://example.com/authorized");
            try (var container = getDefaultContainer()) {
                container.start();
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
        }

        @Test
        @DisplayName("Registering a client with the same ID updates the existing client")
        void registerClientWithSameId() throws IOException, InterruptedException {
            var updatedClient = new DexContainer.Client(
                    firstClient.clientId(),
                    "new-secret",
                    firstClient.redirectUri()
            );
            preconfiguredContainer.withClient(updatedClient);
            var user = preconfiguredContainer.getUser();
            var configuration = Oidc.getConfiguration(preconfiguredContainer.getIssuerUri());

            assertThat(preconfiguredContainer.getClients())
                    .hasSize(2)
                    .containsExactly(updatedClient, secondClient);
            assertThatNoException()
                    .isThrownBy(() -> Oidc.obtainToken(configuration, updatedClient, user));
            assertThatExceptionOfType(Oidc.OidcException.class)
                    .isThrownBy(() -> Oidc.obtainToken(configuration, firstClient, user));
        }

        @Test
        void removeClient() throws IOException, InterruptedException {
            var configuration = Oidc.getConfiguration(preconfiguredContainer.getIssuerUri());
            var user = preconfiguredContainer.getUser();

            var removed = preconfiguredContainer.removeClient(firstClient.clientId());

            assertThat(removed).isEqualTo(firstClient);
            assertThat(preconfiguredContainer.getClients())
                    .hasSize(1)
                    .containsExactly(secondClient);
            assertThatExceptionOfType(Oidc.OidcException.class)
                    .isThrownBy(() -> Oidc.obtainToken(configuration, firstClient, user));
            assertThatNoException()
                    .isThrownBy(() -> Oidc.obtainToken(configuration, secondClient, user));
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
            assertThat(preconfiguredContainer.removeClient("this-client-does-not-exist")).isNull();
        }
    }

    @Nested
    class Users {
        @Test
        void multipleUsers() throws IOException, InterruptedException, URISyntaxException {
            preconfiguredContainer
                    .start();
            var client = preconfiguredContainer.getClient();
            var configuration = Oidc.getConfiguration(preconfiguredContainer.getIssuerUri());

            assertThat(preconfiguredContainer.getUsers())
                    .hasSize(2)
                    .containsExactly(alice, bob);
            assertThat(preconfiguredContainer.getUser()).isEqualTo(alice);
            assertThat(preconfiguredContainer.getUser("alice@example.com")).isEqualTo(alice);
            assertThat(preconfiguredContainer.getUser("bob@example.com")).isEqualTo(bob);

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

        @Test
        void immutableUsers() {
            var testUser = new DexContainer.User("test-user", "test@example.com", "xxxx");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> preconfiguredContainer.getUsers().add(testUser));
            preconfiguredContainer.start();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> preconfiguredContainer.getUsers().add(testUser));
        }

        @Test
        void registerUserAfterStart() throws IOException, InterruptedException, URISyntaxException {
            var user = new DexContainer.User("test-user", "test@example.com", "xxxx");
            try (var container = getDefaultContainer()) {
                container.start();
                var defaultUser = container.getUser();
                container.withUser(user);

                assertThat(container.getUsers())
                        .hasSize(2)
                        .containsExactly(defaultUser, user);
                assertThat(container.getUser()).isEqualTo(defaultUser);
                assertThat(container.getUser("test@example.com")).isEqualTo(user);

                var configuration = Oidc.getConfiguration(container.getIssuerUri());
                var client = container.getClient();

                var idToken = Oidc.obtainToken(configuration, client, user).idTokenClaims();

                assertThat(idToken)
                        .containsEntry("name", "test-user")
                        .containsEntry("email", "test@example.com");
            }
        }

        @Test
        void removeUser() throws IOException, InterruptedException {
            var configuration = Oidc.getConfiguration(preconfiguredContainer.getIssuerUri());
            var client = preconfiguredContainer.getClient();

            var removed = preconfiguredContainer.removeUser(alice.email());

            assertThat(removed).isEqualTo(alice);
            assertThat(preconfiguredContainer.getUsers())
                    .hasSize(1)
                    .containsExactly(bob);
            assertThatExceptionOfType(Oidc.OidcException.class)
                    .isThrownBy(() -> Oidc.obtainToken(configuration, client, alice));
            assertThatNoException()
                    .isThrownBy(() -> Oidc.obtainToken(configuration, client, bob));
        }

        @Test
        void removeUserBeforeStart() {
            var testUser = new DexContainer.User("test-user", "test@example.com", "xxxx");
            try (var container = getDefaultContainer()) {
                container.withUser(testUser);
                assertThat(container.removeUser("test@example.com")).isEqualTo(testUser);
            }
        }

        @Test
        void removeNonExistingUserBeforeStart() {
            var testUser = new DexContainer.User("test-user", "test@example.com", "xxxx");
            try (var container = getDefaultContainer()) {
                container.withUser(testUser);
                assertThat(container.removeUser("non-existing-user@example.com")).isNull();
            }
        }

        @Test
        void removeNonExistingUserAfterStart() {
            assertThat(preconfiguredContainer.removeUser("non-existing-user@example.com")).isNull();
        }
    }

    @NotNull
    private static DexContainer getDefaultContainer() {
        return new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG));
    }

}
