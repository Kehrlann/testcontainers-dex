package wf.garnier.testcontainers.samples.spring;

import java.io.IOException;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wf.garnier.testcontainers.dexidp.DexContainer;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * This test showcases how to run a {@link SpringBootTest} with Testcontainers, using only annotations.
 * Notice that the port needs to be hardcoded for now, see README.md for more detail.
 *
 * @author Daniel Garnier-Moiroux
 * @see <a href="https://spring.io/blog/2023/06/23/improved-testcontainers-support-in-spring-boot-3-1"></a>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutoLifecycleTest {

    // Create a container with a registered Client, with a known redirect URI.
    // See: https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html#oauth2login-sample-redirect-uri
    @Container
    static DexContainer container = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG));

    @LocalServerPort
    private int port;

    // Here we do not autowire a WebClient with @WebMvcTest, because that client
    // can only talk to the Spring app, and wouldn't work with the Dex login page.
    private final WebClient webClient = new WebClient();

    private static final String clientId = "client-id";

    private static final String clientSecret = "client-secret";

    @Test
    void autoLifecycleTest() throws IOException {
        container.withClient(
                new DexContainer.Client(clientId, clientSecret, "http://localhost:%s/login/oauth2/code/dex".formatted(port))
        );

        webClient.getOptions().setRedirectEnabled(true);
        HtmlPage dexLoginPage = webClient.getPage("http://localhost:%s/".formatted(port));
        dexLoginPage.<HtmlInput>getElementByName("login").type(container.getUser().email());
        dexLoginPage.<HtmlInput>getElementByName("password").type(container.getUser().clearTextPassword());

        HtmlPage appPage = dexLoginPage.getElementById("submit-login").click();
        assertThat(appPage.getElementById("name").getTextContent()).isEqualTo("admin");
        assertThat(appPage.getElementById("email").getTextContent()).isEqualTo("admin@example.com");
        assertThat(appPage.getElementById("subject").getTextContent()).isNotBlank();
    }

    @DynamicPropertySource
    static void clientRegistrationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.registration.dex.client-id", () -> clientId);
        registry.add("spring.security.oauth2.client.registration.dex.client-secret", () -> clientSecret);
        registry.add("spring.security.oauth2.client.registration.dex.scope", () -> "openid,email,profile");
        registry.add("spring.security.oauth2.client.provider.dex.issuer-uri", () -> container.getIssuerUri());
    }
}
