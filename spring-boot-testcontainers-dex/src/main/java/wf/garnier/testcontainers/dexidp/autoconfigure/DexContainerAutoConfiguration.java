package wf.garnier.testcontainers.dexidp.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import wf.garnier.testcontainers.dexidp.DexContainer;

/**
 * {@link AutoConfiguration} support for OAuth2 login with a {@link DexContainer}.
 * Supersedes {@link OAuth2ClientAutoConfiguration}, and registers an OAuth2/OpenID client
 * with the running container once the Spring Web Server has a port assigned.
 * <p>
 * So this mutates the container, and that's because in OAuth2 (and OpenID), the Client
 * (Spring app) must know the AuthServer's (dex) issuer URI, and the Client's redirect uri
 * must be registered with the AuthServer. This circular dependency requires either knowledge
 * to be established before the Client and AuthServer start, or one starting after the other
 * and mutating the already-started component. Since both Testcontainers and SpringBootTest
 * tend to provide a random port for their server, we start Dex, point Spring Boot at it, and
 * then add Spring Boot to Dex as a client.
 */
@AutoConfiguration
@AutoConfigureBefore(OAuth2ClientAutoConfiguration.class)
class DexContainerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    @ConditionalOnBean(DexConnectionDetails.class)
    ClientRegistrationRepository clientRegistrationRepository(DexConnectionDetails connectionDetails) {
        var client = ClientRegistrations
                .fromIssuerLocation(connectionDetails.getIssuerUri())
                .registrationId(connectionDetails.getRegistrationName())
                .clientId(connectionDetails.getClientId())
                .clientSecret(connectionDetails.getClientSecret())
                .scope("openid", "email", "profile")
                .build();
        return new InMemoryClientRegistrationRepository(client);
    }

    @Bean
    @ConditionalOnBean(DexConnectionDetails.class)
    ApplicationListener<WebServerInitializedEvent> ready(DexConnectionDetails connectionDetails) {
        return event -> {
            connectionDetails.registerClient(event.getWebServer().getPort());
        };
    }
}
