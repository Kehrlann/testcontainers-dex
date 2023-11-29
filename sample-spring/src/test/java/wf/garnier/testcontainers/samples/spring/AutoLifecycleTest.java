package wf.garnier.testcontainers.samples.spring;

import java.io.IOException;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wf.garnier.testcontainers.dexidp.DexContainer;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * TODO
 *
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = "server.port=1234")
class AutoLifecycleTest {

    @Container
    static DexContainer container = new DexContainer()
            .withClient(new DexContainer.Client("some-client", "some-secret", "http://localhost:1234/login/oauth2/code/dex"));

    // Y U NO AUTOWIRE ?!
    private final WebClient webClient = new WebClient();

    @Test
    void autoLifecycleTest() throws IOException {
        webClient.getOptions().setRedirectEnabled(true);
        HtmlPage dexLoginPage = webClient.getPage("http://localhost:1234/");
        dexLoginPage.<HtmlInput>getElementByName("login").type(container.getUser().email());
        dexLoginPage.<HtmlInput>getElementByName("password").type(container.getUser().clearTextPassword());

        HtmlPage appPage = dexLoginPage.getElementById("submit-login").click();
        assertThat(appPage.getElementById("name").getTextContent()).isEqualTo("admin");
        assertThat(appPage.getElementById("email").getTextContent()).isEqualTo("admin@example.com");
        assertThat(appPage.getElementById("subject").getTextContent()).isNotBlank();
    }

    @DynamicPropertySource
    static void clientRegistrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.registration.dex.client-id", () -> container.getClient().clientId());
        registry.add("spring.security.oauth2.client.registration.dex.client-secret", () -> container.getClient().clientSecret());
        registry.add("spring.security.oauth2.client.registration.dex.redirect-uri", () -> container.getClient().redirectUri());
        registry.add("spring.security.oauth2.client.registration.dex.scope", () -> "openid,email,profile");
        registry.add("spring.security.oauth2.client.provider.dex.issuer-uri", () -> container.getIssuerUri());
    }
}
