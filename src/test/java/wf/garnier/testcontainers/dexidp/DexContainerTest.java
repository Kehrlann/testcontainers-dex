package wf.garnier.testcontainers.dexidp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static java.net.http.HttpResponse.BodyHandlers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class DexContainerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void boots() {
        try (var container = new DexContainer()) {
            container.start();
            assertThat(container.isRunning()).isTrue();
        }
    }

    @Test
    void servesOpenidConfiguration() throws IOException, InterruptedException {
        try (var container = new DexContainer()) {
            container.start();
            var issuerUri = container.getIssuerUri();
            var openidConfigurationUri = URI.create(issuerUri + "/.well-known/openid-configuration");
            var request = HttpRequest.newBuilder(openidConfigurationUri)
                    .GET()
                    .build();
            var httpResponse = HttpClient.newHttpClient()
                    .send(request, BodyHandlers.ofString())
                    .body();
            var configuration = objectMapper.readValue(httpResponse, OpenidConfigurationResponse.class);
            assertThat(configuration.issuer()).isEqualTo(issuerUri);
        }
    }

    @Test
    void issuesToken() throws IOException, InterruptedException, URISyntaxException {
        try (var container = new DexContainer()) {
            container.start();
            var configuration = getConfiguration(container.getIssuerUri());
            var client = container.getClient();
            var user = container.getUser();

            var token = obtainToken(configuration, client, user);

            assertThat(container.getClients()).containsExactly(client);
            assertThat(token.idTokenClaims())
                    .containsEntry("iss", container.getIssuerUri())
                    .containsEntry("aud", client.clientId())
                    .containsEntry("name", user.username())
                    .containsEntry("email", user.email());
        }
    }

    @Nested
    class Clients {
        @Test
        void multipleClients() throws IOException, InterruptedException, URISyntaxException {
            var first = new DexContainer.Client("client-1", "client-1-secret", "https://example.com/authorized");
            var second = new DexContainer.Client("client-2", "client-2-secret", "https://example.com/authorized");

            try (var container = new DexContainer()) {
                container
                        .withClient(first)
                        .withClient(second)
                        .start();

                var configuration = getConfiguration(container.getIssuerUri());
                var user = container.getUser();

                assertThat(container.getClients())
                        .hasSize(2)
                        .containsExactly(first, second);
                assertThat(container.getClient()).isEqualTo(first);

                var firstIdToken = obtainToken(configuration, first, user).idTokenClaims();
                var secondIdToken = obtainToken(configuration, second, user).idTokenClaims();
                assertThat(firstIdToken.get("aud")).isEqualTo("client-1");
                assertThat(secondIdToken.get("aud")).isEqualTo("client-2");
            }
        }

        @Test
        void mustRegisterClientsBeforeStart() {
            var client = new DexContainer.Client("x", "x", "x");

            try (var container = new DexContainer()) {
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
            try (var container = new DexContainer()) {
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
            try (var container = new DexContainer()) {
                container
                        .withUser(alice)
                        .withUser(bob)
                        .start();
                var client = container.getClient();
                var configuration = getConfiguration(container.getIssuerUri());

                assertThat(container.getUsers())
                        .hasSize(2)
                        .containsExactly(alice, bob);
                assertThat(container.getUser()).isEqualTo(alice);

                var aliceIdToken = obtainToken(configuration, client, alice).idTokenClaims();
                var bobIdToken = obtainToken(configuration, client, bob).idTokenClaims();

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
            try (var container = new DexContainer()) {
                container.start();
                var defaultUser = container.getUser();
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(() -> container.withUser(user));

                assertThat(container.getUser()).isEqualTo(defaultUser);
            }
        }

        @Test
        void mustStartBeforeGettingUser() {
            try (var container = new DexContainer()) {
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getUser);
                assertThatExceptionOfType(IllegalStateException.class)
                        .isThrownBy(container::getUsers);
            }
        }
    }

    /**
     * Obtain the Configuration data from  OIDC provider.
     * <p>
     * TODO: move in a utility class.
     *
     * @param issuerUri the {@code issuer identifier}
     * @return the configuration data
     * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OIDC Discovery</a>
     */
    private static OpenidConfigurationResponse getConfiguration(String issuerUri) throws IOException, InterruptedException {
        var openidConfigurationUri = URI.create(issuerUri + "/.well-known/openid-configuration");
        var request = HttpRequest.newBuilder(openidConfigurationUri)
                .GET()
                .build();
        var httpResponse = HttpClient.newHttpClient()
                .send(request, BodyHandlers.ofString())
                .body();
        return objectMapper.readValue(httpResponse, OpenidConfigurationResponse.class);
    }

    /**
     * Perform a full OpenID Connect {@code authorization_code} flow to obtain tokens.
     *
     * @param configuration the configuration data for the Dex container
     * @param client        the OIDC Client to use to make token requests
     * @param user          the User to use to log in
     * @return the full token response
     */
    private static TokenResponse obtainToken(OpenidConfigurationResponse configuration, DexContainer.Client client, DexContainer.User user) throws URISyntaxException, IOException, InterruptedException {
        var authorizationUri = new URIBuilder(configuration.authorizationEndpoint())
                .appendPath("local") // this is for users logging through static passwords
                .addParameter("response_type", "code")
                .addParameter("client_id", client.clientId())
                .addParameter("scope", "openid email profile")
                .addParameter("redirect_uri", client.redirectUri())
                .build();
        var request = HttpRequest.newBuilder(authorizationUri)
                .GET()
                .build();
        var loginRedirect = HttpClient.newHttpClient()
                .send(request, BodyHandlers.discarding())
                .headers()
                .firstValue("location")
                .get();

        var loginUri = URI.create(configuration.issuer()).resolve(loginRedirect);
        var loginBody = new URIBuilder("")
                .addParameter("login", user.email())
                .addParameter("password", user.clearTextPassword())
                .build()
                .getRawQuery();
        var loginRequest = HttpRequest.newBuilder(loginUri)
                .header("content-type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build();

        var redirectUriWithCode = HttpClient.newHttpClient()
                .send(loginRequest, BodyHandlers.discarding())
                .headers()
                .firstValue("location")
                .get();
        var code = URLEncodedUtils.parse(URI.create(redirectUriWithCode), StandardCharsets.UTF_8)
                .stream()
                .filter(nvp -> nvp.getName().equals("code"))
                .findFirst()
                .get()
                .getValue();

        var tokenRequestBody = new URIBuilder("")
                .addParameter("code", code)
                .addParameter("redirect_uri", client.redirectUri())
                .addParameter("grant_type", "authorization_code")
                .build()
                .getRawQuery();
        var httpBasicCredentials = Base64.getUrlEncoder().encodeToString((client.clientId() + ":" + client.clientSecret()).getBytes());
        var tokenRequest = HttpRequest.newBuilder(URI.create(configuration.tokenEndpoint()))
                .header("content-type", "application/x-www-form-urlencoded")
                .header("authorization", "Basic " + httpBasicCredentials)
                .POST(HttpRequest.BodyPublishers.ofString(tokenRequestBody))
                .build();
        var tokenResponse = HttpClient.newHttpClient()
                .send(tokenRequest, BodyHandlers.ofString())
                .body();

        return objectMapper.readValue(tokenResponse, TokenResponse.class);
    }


    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenidConfigurationResponse(String issuer, String authorizationEndpoint, String tokenEndpoint) {
    }


    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(String idToken, String accessToken, String scope) {
        public Map<String, Object> idTokenClaims() {
            return parseJwt(idToken);
        }

        public Map<String, Object> accessTokenClaims() {
            return parseJwt(accessToken);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJwt(String jwt) {
        var parts = jwt.split("\\.");
        var decodedBody = Base64.getUrlDecoder().decode(parts[1]);
        try {
            return (Map<String, Object>) objectMapper.readValue(decodedBody, Map.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
