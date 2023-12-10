package wf.garnier.testcontainers.dexidp.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.apache.hc.core5.net.URIBuilder;
import wf.garnier.testcontainers.dexidp.DexContainer;


public class Oidc {

    private static final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * Obtain the Configuration data from  OIDC provider.
     *
     * @param issuerUri the {@code issuer identifier}
     * @return the configuration data
     * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OIDC Discovery</a>
     */
    public static OpenidConfigurationResponse getConfiguration(String issuerUri) throws IOException, InterruptedException {
        var openidConfigurationUri = URI.create(issuerUri + "/.well-known/openid-configuration");
        var request = HttpRequest.newBuilder(openidConfigurationUri)
                .GET()
                .build();
        var httpResponse = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString())
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
    public static TokenResponse obtainToken(OpenidConfigurationResponse configuration, DexContainer.Client client, DexContainer.User user) throws URISyntaxException, IOException, InterruptedException {
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
                .send(request, HttpResponse.BodyHandlers.discarding())
                .headers()
                .firstValue("location")
                .orElseThrow(() -> new OidcException("Dex did not redirect to the login page"));

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
                .send(loginRequest, HttpResponse.BodyHandlers.discarding())
                .headers()
                .firstValue("location")
                .orElseThrow(() -> new OidcException("Dex did not redirect back to the app with an authorization code"));

        var code = new URIBuilder(redirectUriWithCode)
                .getQueryParams()
                .stream()
                .filter(nvp -> nvp.getName().equals("code"))
                .findFirst()
                .orElseThrow(() -> new OidcException("Missing authorization code in the response"))
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
                .send(tokenRequest, HttpResponse.BodyHandlers.ofString())
                .body();

        return objectMapper.readValue(tokenResponse, TokenResponse.class);
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

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenidConfigurationResponse(String issuer, String authorizationEndpoint, String tokenEndpoint) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public
    record TokenResponse(String idToken, String accessToken, String scope) {
        public Map<String, Object> idTokenClaims() {
            return parseJwt(idToken);
        }

        public Map<String, Object> accessTokenClaims() {
            return parseJwt(accessToken);
        }
    }

    public static class OidcException extends RuntimeException {
        public OidcException(String message) {
            super(message);
        }
    }
}
