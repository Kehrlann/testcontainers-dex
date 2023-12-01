package wf.garnier.testcontainers.dexidp;


import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;


/**
 * Represents a container running the Dex OpenID Connect Provider. It provides a lightweight,
 * OpenID-compliant identity provider for all your integration tests that deal with {@code id_token}
 * flows.
 * <p>
 * Clients and users should not be added after the container is started.
 * There is a check in place, but it's not thread-safe.
 *
 * @see <a href="https://dexidp.io">DexIDP.io</a>
 */
public class DexContainer extends GenericContainer<DexContainer> {

    private static final int DEX_PORT = 5556;

    private List<Client> clients = new ArrayList<>();

    private List<User> users = new ArrayList<>();

    private boolean isConfigured = false;

    private final int hostPort;

    /**
     * Constructs a GenericContainer running Dex {@code v2.37.0}, listening on the
     * provided host port.
     * <p>
     * Dex requires the {@code issuer} property to be defined before the container
     * starts, so the port which consumers will use to reach the container needs to
     * be known before the container starts.
     *
     * @param hostPort the port exposed on the host
     */
    public DexContainer(int hostPort) {
        super("dexidp/dex:v2.37.0");
        // Must be 1-1 mapping because the issuer-uri must match
        this.hostPort = hostPort;
        this.addExposedPort(DEX_PORT); // TODO: explain
        this.addFixedExposedPort(this.hostPort, DEX_PORT);
        this.waitingFor(
                Wait.forHttp("/dex/.well-known/openid-configuration")
                        .forPort(DEX_PORT)
                        .withStartupTimeout(Duration.ofSeconds(10))
        );
        this.withCommand(
                "dex",
                "serve",
                "/etc/dex/dex.yml"
        );
    }

    @Override
    protected void configure() {
        this.isConfigured = true;
        if (clients.isEmpty()) {
            clients = Collections.singletonList(new Client("example-app", "ZXhhbXBsZS1hcHAtc2VjcmV0", "http://127.0.0.1:5555/callback"));
        } else {
            clients = Collections.unmodifiableList(clients);
        }
        if (users.isEmpty()) {
            users = Collections.singletonList(new User("admin", "admin@example.com", "password"));
        } else {
            users = Collections.unmodifiableList(users);
        }
        this.withCopyToContainer(Transferable.of(configuration()), "/etc/dex/dex.yml");
    }

    /**
     * Return the OpenID Connect Provider's {@code issuer identifier}. It will match whatever is in
     * the OpenID Configuration Document.
     *
     * @return the issuer URI, in String format.
     * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Terminology">OpenID Connect Core - Terminology</a>
     */
    public String getIssuerUri() {
        return "http://%s:%s/dex".formatted(getHost(), this.hostPort);
    }

    private String configuration() {
        var baseConfiguration = """
                issuer: %s
                storage:
                  type: sqlite3
                  config:
                    file: /etc/dex/dex.db
                web:
                  http: 0.0.0.0:%s
                enablePasswordDB: true
                oauth2:
                    skipApprovalScreen: true
                """.formatted(getIssuerUri(), DEX_PORT);
        return baseConfiguration + getUserConfiguration() + getClientConfiguration();
    }

    private String getUserConfiguration() {
        var users = getUsers().stream()
                .map(u -> """
                        - username: %s
                          email: %s
                          userID: %s
                          hash: %s
                        """.formatted(u.username(), u.email(), u.uuid(), u.bcryptPassword())
                )
                .collect(Collectors.joining())
                .indent(2);
        return """
                staticPasswords:
                %s
                """
                .formatted(users);
    }

    private String getClientConfiguration() {
        var clients = getClients().stream()
                .map(c -> """
                        - id: %s
                          name: %s
                          secret: %s
                          redirectURIs:
                            - %s
                        """
                        .formatted(c.clientId(), c.clientId(), c.clientSecret(), c.redirectUri()))
                .map(c -> c.indent(2))
                .collect(Collectors.joining());
        return """
                staticClients:
                %s
                """
                .formatted(clients);
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
        if (isConfigured) {
            throw new IllegalStateException("clients cannot be added after the container is started");
        }
        clients.add(client);
        return self();
    }

    /**
     * Get an OAuth2 Client to interact with the OpenID Provider. When there are multiple clients,
     * only return the first client. When the user has defined no client, return a default client.
     * <p>
     * This method MUST NOT be called before the container is started.
     *
     * @return the client
     * @throws IllegalStateException when called before the container is started
     * @see #getClients()
     */
    public Client getClient() {
        return getClients().get(0);
    }

    /**
     * Get the list of all defined OAuth2 Clients to interact with the OpenID Provider. When the user has
     * defined no client, returns a single default client.
     * <p>
     * This method MUST NOT be called before the container is started.
     *
     * @return the clients
     * @throws IllegalStateException when called before the container is started
     */
    public List<Client> getClients() {
        if (!isConfigured) {
            throw new IllegalStateException("must start the container before accessing the clients");
        }
        return clients;
    }

    /**
     * Add a User that can log in with the OpenID Provider.
     * <p>
     * This MUST NOT be called after the container is started.
     * <p>
     * This is optional. When not called, a default user is provided.
     *
     * @param user the user
     * @return this instance for further customization
     * @throws IllegalStateException when called after the container is started
     * @see #getUsers()
     */
    public DexContainer withUser(User user) {
        if (isConfigured) {
            throw new IllegalStateException("users cannot be added after the container is started");
        }
        users.add(user);
        return self();
    }

    /**
     * Return a User that can log in with the OpenID provider. When multiple users are defined,
     * returns the first one. When no user is defined, returns a default user.
     * <p>
     * This method MUST NOT be called before the container is started.
     *
     * @return the user
     * @throws IllegalStateException when called before the container is started
     */
    public User getUser() {
        return getUsers().get(0);
    }


    /**
     * Return the list of Users that can log in with the OpenID provider. When no user is defined,
     * returns a default user.
     * <p>
     * This method MUST NOT be called before the container is started.
     *
     * @return the users
     * @throws IllegalStateException when called before the container is started
     */
    public List<User> getUsers() {
        if (!isConfigured) {
            throw new IllegalStateException("must start the container before accessing the users");
        }
        return users;
    }

    /**
     * Represents an OAuth 2 / OpenID Connect Client.
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