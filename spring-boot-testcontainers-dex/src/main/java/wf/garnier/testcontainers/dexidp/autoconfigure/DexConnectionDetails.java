package wf.garnier.testcontainers.dexidp.autoconfigure;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import wf.garnier.testcontainers.dexidp.DexContainer;

/**
 * {@link ConnectionDetails} for a running {@link DexContainer}. It has a {@link #registerClient(int port)}
 * which mutates the running container, by adding a client to the running Dex OpenID Provider.
 */
public interface DexConnectionDetails extends ConnectionDetails {

    /**
     * The name of the {@code spring.security.oauth2.client.registration}.
     *
     * @return {@code dex}
     */
    default String getRegistrationName() {
        return "dex";
    }

    /**
     * The Issuer URI of the running Dex container.
     * Used for {@code spring.security.oauth2.client.provider.dex.issuer-uri}.
     *
     * @return the Issuer URI
     * @see DexContainer#getIssuerUri()
     * @see ClientRegistration.ProviderDetails#getIssuerUri()
     */
    String getIssuerUri();

    /**
     * The {@code client_id} for an OAuth2/OpenID client in the running Dex container.
     * Used for {@code spring.security.oauth2.client.registration.dex.client-id}.
     *
     * @return the client_id
     * @see ClientRegistration#getClientId()
     */
    String getClientId();

    /**
     * The {@code client_secret} for an OAuth2/OpenID client in the running Dex container.
     * Used for {@code spring.security.oauth2.client.registration.dex.client-secret}.
     *
     * @return the client_secret
     * @see ClientRegistration#getClientSecret()
     */
    String getClientSecret();

    /**
     * Register a client with the Dex OpenID Provider. The client will use {@link #getClientId()} and
     * {@link #getClientSecret()}. The {@code redirect_uri} will be:
     * {@code http://localhost:${port}/login/oauth2/code/${registrationName}}
     *
     * @param port the port of the running Spring application
     */
    void registerClient(int port);

}
