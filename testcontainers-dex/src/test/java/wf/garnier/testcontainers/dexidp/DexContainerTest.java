package wf.garnier.testcontainers.dexidp;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import wf.garnier.testcontainers.dexidp.utils.Oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DexContainerTest {

    private static final int TEST_PORT = 5557;

    @Test
    void boots() {
        try (var container = new DexContainer(TEST_PORT)) {
            container.start();
            assertThat(container.isRunning()).isTrue();
        }
    }

    @Test
    void servesOpenidConfiguration() throws IOException, InterruptedException {
        try (var container = new DexContainer(TEST_PORT)) {
            container.start();
            var configuration = Oidc.getConfiguration(container.getIssuerUri());
            assertThat(configuration.issuer()).isEqualTo(container.getIssuerUri());
        }
    }

    @Test
    void issuesToken() throws IOException, InterruptedException, URISyntaxException {
        try (var container = new DexContainer(TEST_PORT)) {
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
    }

    @Nested
    class Clients {
        @Test
        void multipleClients() throws IOException, InterruptedException, URISyntaxException {
            var first = new DexContainer.Client("client-1", "client-1-secret", "https://example.com/authorized");
            var second = new DexContainer.Client("client-2", "client-2-secret", "https://example.com/authorized");

            try (var container = new DexContainer(TEST_PORT)) {
                container
                        .withClient(first)
                        .withClient(second)
                        .start();

                var configuration = Oidc.getConfiguration(container.getIssuerUri());
                var user = container.getUser();

                assertThat(container.getClients())
                        .hasSize(2)
                        .containsExactly(first, second);
                assertThat(container.getClient()).isEqualTo(first);

                var responseFirst = Oidc.obtainToken(configuration, first, user);
                assertThat(responseFirst.idTokenClaims().get("aud")).isEqualTo("client-1");
                assertThat(responseFirst.accessTokenClaims().get("aud")).isEqualTo("client-1");
                var responseSecond = Oidc.obtainToken(configuration, second, user);
                assertThat(responseSecond.idTokenClaims().get("aud")).isEqualTo("client-2");
                assertThat(responseSecond.accessTokenClaims().get("aud")).isEqualTo("client-2");
            }
        }

        @Test
        void mustRegisterClientsBeforeStart() {
            var client = new DexContainer.Client("x", "x", "x");

            try (var container = new DexContainer(TEST_PORT)) {
                container.start();
                var defaultClient = container.getClient();
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(() -> container.withClient(client));

                assertThat(container.getClients())
                        .hasSize(1)
                        .containsExactly(defaultClient);
            }
        }

        @Test
        void mustStartBeforeGettingClient() {
            try (var container = new DexContainer(TEST_PORT)) {
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getClient);
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getClients);
            }
        }
    }

    @Nested
    class Users {
        @Test
        void multipleUsers() throws IOException, InterruptedException, URISyntaxException {
            var alice = new DexContainer.User("alice", "alice@example.com", "alice-password");
            var bob = new DexContainer.User("bob", "bob@example.com", "bob-password");
            try (var container = new DexContainer(TEST_PORT)) {
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
            try (var container = new DexContainer(TEST_PORT)) {
                container.start();
                var defaultUser = container.getUser();
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(() -> container.withUser(user));

                assertThat(container.getUser()).isEqualTo(defaultUser);
            }
        }

        @Test
        void mustStartBeforeGettingUser() {
            try (var container = new DexContainer(TEST_PORT)) {
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getUser);
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getUsers);
            }
        }
    }

}
