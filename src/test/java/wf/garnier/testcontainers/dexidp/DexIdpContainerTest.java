package wf.garnier.testcontainers.dexidp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static java.net.http.HttpResponse.*;
import static org.assertj.core.api.Assertions.assertThat;

class DexIdpContainerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    void boots() throws IOException, InterruptedException {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenidConfigurationResponse(String issuer) {
    }

}
