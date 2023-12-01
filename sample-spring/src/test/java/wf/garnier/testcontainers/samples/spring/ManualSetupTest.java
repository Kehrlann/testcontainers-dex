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
 * TODO
 */
class ManualSetupTest {

    private final WebClient webClient = new WebClient();

    @Test
    void manualSetupTest() throws IOException {
        var client = new DexContainer.Client("some-client", "some-secret", "http://localhost:2345/login/oauth2/code/dex");
        try (var container = new DexContainer()) {
            container.withClient(client).start();
            var env = new MockEnvironment()
                    .withProperty("server.port=", "2345")
                    .withProperty("spring.security.oauth2.client.registration.dex.client-id", container.getClient().clientId())
                    .withProperty("spring.security.oauth2.client.registration.dex.client-secret", container.getClient().clientSecret())
                    .withProperty("spring.security.oauth2.client.registration.dex.redirect-uri", container.getClient().redirectUri())
                    .withProperty("spring.security.oauth2.client.registration.dex.scope", "openid,email,profile")
                    .withProperty("spring.security.oauth2.client.provider.dex.issuer-uri", container.getIssuerUri());
            var appBuilder = new SpringApplicationBuilder(SampleSpringApplication.class)
                    .environment(env);

            try (var app = appBuilder.run()) {
                webClient.getOptions().setRedirectEnabled(true);
                HtmlPage dexLoginPage = webClient.getPage("http://localhost:2345/");
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
