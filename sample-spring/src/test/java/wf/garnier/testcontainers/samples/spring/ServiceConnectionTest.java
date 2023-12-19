package wf.garnier.testcontainers.samples.spring;

import java.io.IOException;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wf.garnier.testcontainers.dexidp.DexContainer;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * This test showcases how to run a {@link SpringBootTest} with Testcontainers, using the {@link ServiceConnection}
 * abstraction.
 *
 * @author Daniel Garnier-Moiroux
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceConnectionTest {

    @Container
    @ServiceConnection
    static DexContainer container = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG));

    @LocalServerPort
    private int port;

    // Here we do not autowire a WebClient with @WebMvcTest, because that client
    // can only talk to the Spring app, and wouldn't work with the Dex login page.
    private final WebClient webClient = new WebClient();

    @Test
    void autoLifecycleTest() throws IOException {
        webClient.getOptions().setRedirectEnabled(true);
        HtmlPage dexLoginPage = webClient.getPage("http://localhost:%s/".formatted(port));
        dexLoginPage.<HtmlInput>getElementByName("login").type(container.getUser().email());
        dexLoginPage.<HtmlInput>getElementByName("password").type(container.getUser().clearTextPassword());

        HtmlPage appPage = dexLoginPage.getElementById("submit-login").click();
        assertThat(appPage.getElementById("name").getTextContent()).isEqualTo("admin");
        assertThat(appPage.getElementById("email").getTextContent()).isEqualTo("admin@example.com");
        assertThat(appPage.getElementById("subject").getTextContent()).isNotBlank();
    }
}
