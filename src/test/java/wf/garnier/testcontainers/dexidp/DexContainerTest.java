package wf.garnier.testcontainers.dexidp;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Test;

import static java.net.http.HttpResponse.BodyHandlers;
import static org.assertj.core.api.Assertions.assertThat;

class DexContainerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();


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
    void getToken() throws IOException, InterruptedException, URISyntaxException {
        try (var container = new DexContainer()) {
            container.start();
            var configuration = getConfiguration(container.getIssuerUri());
            var client = container.getClient();
            var user = container.getUser();

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

            var loginUri = URI.create(container.getIssuerUri()).resolve(loginRedirect);
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

            var parsedResponse = objectMapper.readValue(tokenResponse, TokenResponse.class);
            assertThat(parsedResponse.idToken()).isNotBlank();
        }
    }

    private OpenidConfigurationResponse getConfiguration(String issuerUri) throws IOException, InterruptedException {
        var openidConfigurationUri = URI.create(issuerUri + "/.well-known/openid-configuration");
        var request = HttpRequest.newBuilder(openidConfigurationUri)
                .GET()
                .build();
        var httpResponse = HttpClient.newHttpClient()
                .send(request, BodyHandlers.ofString())
                .body();
        return objectMapper.readValue(httpResponse, OpenidConfigurationResponse.class);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenidConfigurationResponse(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint
    ) {
    }


    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            String idToken,
            String accessToken,
            String scope
    ) {
    }

}
