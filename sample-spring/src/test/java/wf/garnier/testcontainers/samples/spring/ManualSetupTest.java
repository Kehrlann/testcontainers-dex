package wf.garnier.testcontainers.samples.spring;

import java.io.IOException;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;
import wf.garnier.testcontainers.dexidp.DexContainer;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * This test showcases how to run a Spring Boot app for testing, without using annotations,
 * and manually managing the lifecycle of both the TestContainer and the Boot app.
 * Notice that the port needs to be hardcoded for now, see README.md for more detail.
 *
 * @author Daniel Garnier-Moiroux
 */
class ManualSetupTest {

    private final WebClient webClient = new WebClient();

    private static final String clientId = "client-id";

    private static final String clientSecret = "client-secret";


    @Test
    void manualSetupTest() throws IOException {
        // The redirect URI is a well known Boot uri
        // See: https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html#oauth2login-sample-redirect-uri
        try (var container = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG))) {
            container.start();
            var env = new MockEnvironment()
                    .withProperty("spring.security.oauth2.client.registration.dex.client-id", clientId)
                    .withProperty("spring.security.oauth2.client.registration.dex.client-secret", clientSecret)
                    .withProperty("spring.security.oauth2.client.registration.dex.scope", "openid,email,profile")
                    .withProperty("spring.security.oauth2.client.provider.dex.issuer-uri", container.getIssuerUri());
            var appBuilder = new SpringApplicationBuilder(SampleSpringApplication.class)
                    .environment(env);

            try (var app = appBuilder.run()) {
                var localServerPort = Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
                var client = new DexContainer.Client(clientId, clientSecret, "http://localhost:%s/login/oauth2/code/dex".formatted(localServerPort));
                container.withClient(client);

                webClient.getOptions().setRedirectEnabled(true);
                HtmlPage dexLoginPage = webClient.getPage("http://localhost:%s/".formatted(localServerPort));
                dexLoginPage.<HtmlInput>getElementByName("login").type(container.getUser().email());
                dexLoginPage.<HtmlInput>getElementByName("password").type(container.getUser().clearTextPassword());

                HtmlPage appPage = dexLoginPage.getElementById("submit-login").click();
                assertThat(appPage.getElementById("name").getTextContent()).isEqualTo("admin");
                assertThat(appPage.getElementById("email").getTextContent()).isEqualTo("admin@example.com");
                assertThat(appPage.getElementById("subject").getTextContent()).isNotBlank();
            }
        }
    }

}
