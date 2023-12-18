package wf.garnier.testcontainers.dexidp.autoconfigure;

import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;
import org.springframework.web.util.UriComponentsBuilder;
import wf.garnier.testcontainers.dexidp.DexContainer;


/**
 * {@link ContainerConnectionDetailsFactory} to create {@link DexConnectionDetails} for {@link DexContainer}.
 */
public class DexConnectionDetailsFactory extends ContainerConnectionDetailsFactory<DexContainer, DexConnectionDetails> {

    @Override
    protected DexConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<DexContainer> source) {
        return new DexContainerConnectionDetails(source);
    }

    private static final class DexContainerConnectionDetails extends ContainerConnectionDetailsFactory.ContainerConnectionDetails<DexContainer> implements DexConnectionDetails {

        private DexContainer.Client client = new DexContainer.Client("spring-client", "spring-secret", "<will be updated>");

        private DexContainerConnectionDetails(ContainerConnectionSource<DexContainer> source) {
            super(source);
        }

        @Override
        public String getIssuerUri() {
            return getContainer().getIssuerUri();
        }

        @Override
        public String getClientId() {
            return client.clientId();
        }

        @Override
        public String getClientSecret() {
            return client.clientSecret();
        }

        @Override
        public void registerClient(int port) {
            var redirectUri = UriComponentsBuilder.fromHttpUrl("http://localhost")
                    .port(port)
                    .path("/login/oauth2/code/" + getRegistrationName())
                    .toUriString();
            client = new DexContainer.Client(
                    client.clientId(),
                    client.clientSecret(),
                    redirectUri
            );
            getContainer().withClient(client);
        }
    }
}


