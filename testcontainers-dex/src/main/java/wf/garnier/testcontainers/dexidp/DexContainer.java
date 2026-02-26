package wf.garnier.testcontainers.dexidp;


import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import wf.garnier.testcontainers.dexidp.grpc.DexGrpc;
import wf.garnier.testcontainers.dexidp.grpc.DexGrpcApi;


/**
 * Represents a container running the Dex OpenID Connect Provider. It provides a lightweight,
 * OpenID-compliant identity provider for all your integration tests that deal with {@code id_token}
 * flows.
 * <p>
 * Clients and users should not be added after the container is started.
 * There is a check in place, but it's not thread-safe.
 *
 * @author Daniel Garnier-Moiroux
 * @see <a href="https://dexidp.io">DexIDP.io</a>
 */
public class DexContainer extends GenericContainer<DexContainer> {

    /**
     * The image that this container is tested against.
     */
    public static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("dexidp/dex");

    /**
     * The tag that this container is tested against.
     */
    public static final String DEFAULT_TAG = "v2.45.0";

    private static final int DEX_HTTP_PORT = 5556;

    private static final int DEX_GRPC_PORT = 5557;

    private static final String DEX_CONFIG_FILE = "/var/dex/dex.yml";

    private final Map<String, Client> clients = new LinkedHashMap<>();

    private final Map<String, User> users = new LinkedHashMap<>();

    private boolean isStarted = false;

    private DexGrpc.DexBlockingStub grpcStub = null;

    private ManagedChannel channel;

    /**
     * Constructs a GenericContainer running Dex.
     *
     * @param dockerImageName - the Docker image to use.
     * @see DexContainer#DEFAULT_IMAGE_NAME
     * @see DexContainer#DEFAULT_TAG
     */
    public DexContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        this.addExposedPort(DEX_HTTP_PORT);
        this.addExposedPort(DEX_GRPC_PORT);
        this.waitingFor(
                Wait.forHttp("/dex/.well-known/openid-configuration")
                        .forPort(DEX_HTTP_PORT)
                        .withStartupTimeout(Duration.ofSeconds(10))
        );
        //@formatter:off
        this.withCommand(
                "/bin/sh",
                "-c",
                // We wait for the config file to be present before starting the Dex process itself.
                // The config file must be written AFTER the container is started, see #containerIsStarting
                """
                while [[ ! -f %s ]]; do sleep 1; echo "Waiting for configuration file..."; done;
                dex serve %s
                """.formatted(DEX_CONFIG_FILE, DEX_CONFIG_FILE)
        );
        //@formatter:on
    }

    /**
     * Hack. Write the Dex configuration after the container has started.
     * <p>
     * The Dex configuration must contain an {@code issuer} property, that represents the publicly
     * accessible URL of the running OpenID Provider. It MUST be {@code http://getHost():getMappedPort(DEX_PORT)},
     * so that it reflects the port that is exposed on the host.
     * <p>
     * There is a lifecycle issue. To obtain the mapped port (and write the config file), the container must first
     * be started. To start the dex process, the config file must be present. So the {@code dex serve} command
     * cannot be the launch command of the container, otherwise we would have a circular dependency. Instead, the
     * launch command waits for the file to be present and then runs dex. Right after the command is fired,
     * this method is called, we grab the mapped port, and we write the config file to the correct location.
     *
     * @param containerInfo - unused (it uses #getMappedPort() under the hood though)
     */
    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        try {
            var result = this.execInContainer("/bin/sh", "-c", "echo '%s' > %s".formatted(configuration(), DEX_CONFIG_FILE));
            if (result.getExitCode() != 0) {
                throw new RuntimeException("Could not write config file in container. Result details: " + result);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Could not write config file in container", e);
        }
    }

    /**
     * When the container is started, and the Dex Process is running, open a private gRPC channel to register
     * clients and users.
     *
     * @param containerInfo ignored
     */
    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        isStarted = true;
        channel = ManagedChannelBuilder.forAddress(getHost(), getMappedPort(DEX_GRPC_PORT))
                .usePlaintext()
                .build();
        grpcStub = DexGrpc.newBlockingStub(channel);

        if (clients.isEmpty()) {
            var defaultClient = new Client("example-app", "ZXhhbXBsZS1hcHAtc2VjcmV0", "http://127.0.0.1:5555/callback");
            clients.put(defaultClient.clientId(), defaultClient);
        }
        clients.values().forEach(this::registerClient);

        if (users.isEmpty()) {
            var defaultUser = new User("admin", "admin@example.com", "password");
            users.put(defaultUser.email(), defaultUser);
        }
        users.values().forEach(this::registerUser);
    }

    /**
     * When the container is stopping, close the open gRPC channel.
     *
     * @param containerInfo ignored
     */
    @Override
    protected void containerIsStopping(InspectContainerResponse containerInfo) {
        if (!channel.isShutdown()) {
            channel.shutdown();
        }
        grpcStub = null;
        channel = null;
    }


    /**
     * Return the OpenID Connect Provider's {@code issuer identifier}. It will match whatever is in
     * the OpenID Configuration Document.
     * <p>
     * The container MUST be started before calling this method.
     *
     * @return the issuer URI, in String format.
     * @throws IllegalStateException if the container is not started
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Terminology">OpenID Connect Core - Terminology</a>
     */
    public String getIssuerUri() {
        if (!this.isStarted) {
            throw new IllegalStateException("Issuer URI can only be obtained after the container has started.");
        }
        return templateIssuerUri();
    }


    /**
     * Template the issuer URI from host and port.
     * <p>
     * The container MUST be created before calling this method, in order get the mapped port.
     *
     * @return the issuer URI
     */
    private String templateIssuerUri() {
        return "http://%s:%s/dex".formatted(getHost(), getMappedPort(DEX_HTTP_PORT));
    }

    /**
     * Produces the configuration file that is going to be used in the container, used when the
     * container is starting.
     * <p>
     * The container MUST be created before calling this method.
     *
     * @return the YAML configuration
     * @see <a href="https://dexidp.io/docs/getting-started/#configuration">Dex > Getting Started > Configuration</a>
     * @see #templateIssuerUri()
     */
    protected String configuration() {
        var baseConfiguration = """
                issuer: %s
                storage:
                  type: sqlite3
                  config:
                    file: /etc/dex/dex.db
                web:
                  http: 0.0.0.0:%s
                grpc:
                  addr: 0.0.0.0:%s
                enablePasswordDB: true
                oauth2:
                    skipApprovalScreen: true
                """.formatted(templateIssuerUri(), DEX_HTTP_PORT, DEX_GRPC_PORT);
        return baseConfiguration;
    }

    /**
     * Add an OAuth2 Client capable of interacting with the OpenID provider.
     * <p>
     * This is optional. When not called, a default client is provided.
     *
     * @param client the client to add
     * @return this instance for further configuration
     * @see #getClients()
     */
    public DexContainer withClient(Client client) {
        if (isStarted) {
            // Un-register the client: if it does not exist, it's a no-op.
            // If the client exists, then this is roughly equivalent to "updating"
            // the client. Some internal state _may_ be different, but that is
            // not relevant to our use-case.
            unregisterClient(client.clientId());
            registerClient(client);
        }
        clients.put(client.clientId(), client);
        return self();
    }

    /**
     * Get an OAuth2 Client to interact with the OpenID Provider. When there are multiple clients,
     * only return the first client. When the user has defined no client, return a default client.
     *
     * @return the client
     */
    public Client getClient() {
        return getClients().get(0);
    }

    /**
     * Get an OAuth2 Client to interact with the OpenID Provider, by {@code client_id}.
     *
     * @param clientId the {@code client_id} of the Client
     * @return the Client
     */
    public Client getClient(String clientId) {
        return clients.get(clientId);
    }

    /**
     * Get the list of all defined OAuth2 Clients to interact with the OpenID Provider. When the user has
     * defined no client, returns a single default client.
     *
     * @return the clients
     */
    public List<Client> getClients() {
        var clients = new ArrayList<>(this.clients.values());
        return Collections.unmodifiableList(clients);
    }


    /**
     * Remove an OAuth2 Client from the identity provider, by {@code client_id}
     *
     * @param clientId - the client_id of the client to remove
     * @return the removed client, or {@code null} if there was no client registered under this id.
     */
    @Nullable
    public Client removeClient(String clientId) {
        if (isStarted) {
            unregisterClient(clientId);
        }
        return clients.remove(clientId);
    }

    /**
     * Register the client with the running Dex IDP. The container must be started for this work.
     *
     * @param client the client to register
     */
    private void registerClient(Client client) {
        var grpcClient = DexGrpcApi.Client.newBuilder()
                .setId(client.clientId())
                .setSecret(client.clientSecret())
                .addRedirectUris(client.redirectUri());
        var request = DexGrpcApi.CreateClientReq.newBuilder()
                .setClient(grpcClient)
                .build();
        grpcStub.createClient(request);
    }

    /**
     * Unregister the client with the running Dex IDP. The container must be started for this work.
     *
     * @param clientId the {@code client_id} of the client to unregister
     */
    private void unregisterClient(String clientId) {
        var request = DexGrpcApi.DeleteClientReq.newBuilder()
                .setId(clientId)
                .build();
        grpcStub.deleteClient(request);
    }

    /**
     * Add a User that can log in with the OpenID Provider.
     *
     * @param user the user
     * @return this instance for further customization
     * @see #getUsers()
     */
    public DexContainer withUser(User user) {
        if (isStarted) {
            // Un-register the user: if it does not exist, it's a no-op.
            // If the user exists, then this is roughly equivalent to "updating"
            // the user. Some internal state _may_ be different, but that is
            // not relevant to our use-case.
            unregisterUser(user.email());
            registerUser(user);
        }
        users.put(user.email(), user);
        return self();
    }

    /**
     * Return a User that can log in with the OpenID provider. When multiple users are defined,
     * returns the first one. When no user is defined, returns a default user.
     *
     * @return the user
     */
    public User getUser() {
        return getUsers().get(0);
    }

    /**
     * Return a User that can log in with the OpenID provider, by {@code email}.
     *
     * @param email the email on
     * @return the user
     */
    public User getUser(String email) {
        return users.get(email);
    }

    /**
     * Return the list of Users that can log in with the OpenID provider. When no user is defined,
     * returns a default user.
     *
     * @return the users
     */
    public List<User> getUsers() {
        var users = new ArrayList<>(this.users.values());
        return Collections.unmodifiableList(users);
    }


    /**
     * Remove an User from the identity provider, by {@code email}.
     *
     * @param email - the email of the user to remove
     * @return the removed user, or {@code null} if there was no user registered under this email.
     */
    public User removeUser(String email) {
        if (isStarted) {
            unregisterUser(email);
        }
        return users.remove(email);
    }

    /**
     * Register the user with the running Dex IDP. The container must be started for this work.
     *
     * @param user the user to register
     */
    private void registerUser(User user) {
        var password = DexGrpcApi.Password.newBuilder()
                .setEmail(user.email())
                .setUserId(user.uuid())
                .setHash(ByteString.copyFromUtf8(user.bcryptPassword()))
                .setUsername(user.username());
        var request = DexGrpcApi.CreatePasswordReq.newBuilder()
                .setPassword(password)
                .build();
        grpcStub.createPassword(request);
    }

    /**
     * Unregister the user with the running Dex IDP. The container must be started for this work.
     *
     * @param email the email of the user to unregister
     */
    private void unregisterUser(String email) {
        var request = DexGrpcApi.DeletePasswordReq.newBuilder()
                .setEmail(email)
                .build();
        grpcStub.deletePassword(request);
    }

    /**
     * Represents an OAuth 2 / OpenID Connect Client.
     *
     * @author Daniel Garnier-Moiroux
     */
    public record Client(
            @NotNull @NotBlank String clientId,
            @NotNull @NotBlank String clientSecret,
            @NotNull @NotBlank String redirectUri
    ) {

        /**
         * Construct a new OAuth 2 / OpenID Connect Client.
         *
         * @param clientId     - the client_id, not null, not blank
         * @param clientSecret - the client_secret, not null, not blank
         * @param redirectUri  - the redirectUri, not null, not blank
         * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2">RFC 6749 - 2.  Client Registration</a>
         */
        public Client {
            Validation.assertNotBlank(clientId, "clientId");
            Validation.assertNotBlank(clientSecret, "clientSecret");
            Validation.assertNotBlank(redirectUri, "redirectUri");
        }
    }

    /**
     * Represents a "User" that may log in with an e-mail and a password. Internally, the
     * password is bcrypt-hashed. The {@link #username()} is used for the {@code name} claim
     * in Dex-issued {@code id_token}s.
     *
     * @author Daniel Garnier-Moiroux
     */
    public static final class User {
        private final String username;
        private final String email;
        private final String clearTextPassword;
        private final String bcryptPassword;
        private final String uuid;

        /**
         * Construct a new User that can log in with username and password.
         *
         * @param username          the login username, not null, not blank
         * @param email             the email, used in the id_token, not null, not blank
         * @param clearTextPassword the password used to log in, not null, not blank
         */
        public User(
                @NotNull @NotBlank String username,
                @NotNull @NotBlank String email,
                @NotNull @NotBlank String clearTextPassword
        ) {
            Validation.assertNotBlank(username, "username");
            Validation.assertNotBlank(email, "email");
            Validation.assertNotBlank(clearTextPassword, "clearTextPassword");
            this.username = username;
            this.email = email;
            this.clearTextPassword = clearTextPassword;
            this.bcryptPassword = BCrypt.hashpw(clearTextPassword, BCrypt.gensalt());
            this.uuid = UUID.randomUUID().toString();
        }

        /**
         * Get the user's log in username.
         *
         * @return the username
         */
        public String username() {
            return username;
        }

        /**
         * Get the user's email.
         *
         * @return the email
         */
        public String email() {
            return email;
        }

        /**
         * Get the user's password, in clear-text, to log in.
         *
         * @return the password, in clear text
         */
        public String clearTextPassword() {
            return clearTextPassword;
        }

        /**
         * Get the user's password, bcrypt-hashed. Used by Dex's configuration file.
         *
         * @return the bcrypt hash of the {@link #clearTextPassword()}
         */
        public String bcryptPassword() {
            return bcryptPassword;
        }


        private String uuid() {
            return uuid;
        }

        @Override
        public int hashCode() {
            return Objects.hash(username, clearTextPassword);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return Objects.equals(username, user.username)
                    && Objects.equals(email, user.email)
                    && Objects.equals(clearTextPassword, user.clearTextPassword)
                    && Objects.equals(bcryptPassword, user.bcryptPassword)
                    && Objects.equals(uuid, user.uuid);
        }

        @Override
        public String toString() {
            return "User{" +
                    "username='" + username + '\'' +
                    ", email='" + email + '\'' +
                    ", clearTextPassword='" + clearTextPassword + '\'' +
                    ", bcryptPassword='" + bcryptPassword + '\'' +
                    ", uuid='" + uuid + '\'' +
                    '}';
        }
    }


}