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

// TODO:
// - get openid configuration data
// - (test) userinfo endpoint
// - document all the things!
// - use ephemeral ports in the issuer (pre-load script _inside_ the started container)
// - RELEASE!
// - samples

/**
 * Represents a container running the Dex OpenID Connect Provider. It provides a lightweight,
 * OpenID-compliant identity provider for all your integration tests that deal with {@code id_token}
 * flows.
 * <p></p>
 * Clients and users should not be added after the container is started.
 * There is a check in place, but it's not thread-safe.
 *
 * @see <a href="https://dexidp.io">DexIDP.io</a>
 */
public class DexContainer extends GenericContainer<DexContainer> {

    private final int DEX_PORT = 5556;

    private List<Client> clients = new ArrayList<>();

    private List<User> users = new ArrayList<>();

    private boolean isConfigured = false;

    public DexContainer() {
        super("dexidp/dex:v2.37.0");
        // Must be 1-1 mapping because the issuer-uri must match
        this.setPortBindings(Collections.singletonList("%s:%s".formatted(DEX_PORT, DEX_PORT)));
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
        return "http://%s:%s/dex".formatted(getHost(), DEX_PORT);
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

    public DexContainer withClient(Client client) {
        if (isConfigured) {
            throw new IllegalStateException("clients cannot be added after the container is started");
        }
        clients.add(client);
        return self();
    }

    public Client getClient() {
        if (!isConfigured) {
            throw new IllegalStateException("must start the container before accessing the clients");
        }
        return clients.get(0);
    }

    public List<Client> getClients() {
        if (!isConfigured) {
            throw new IllegalStateException("must start the container before accessing the clients");
        }
        return clients;
    }

    public DexContainer withUser(User user) {
        if (isConfigured) {
            throw new IllegalStateException("users cannot be added after the container is started");
        }
        users.add(user);
        return self();
    }

    public User getUser() {
        if (!isConfigured) {
            throw new IllegalStateException("must start the container before accessing the users");
        }
        return users.get(0);
    }

    public List<User> getUsers() {
        if (!isConfigured) {
            throw new IllegalStateException("must start the container before accessing the users");
        }
        return users;
    }

    /**
     * Represents an OAuth 2 / OpenID Connect Client.
     *
     * @param clientId     - the client_id
     * @param clientSecret - the client_secret
     * @param redirectUri  - the redirectUri
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2">RFC 6749 - 2.  Client Registration</a>
     */
    public record Client(
            @NotNull @NotBlank String clientId,
            @NotNull @NotBlank String clientSecret,
            @NotNull @NotBlank String redirectUri
    ) {

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

        public String username() {
            return username;
        }

        public String email() {
            return email;
        }

        private String uuid() {
            return uuid;
        }

        public String clearTextPassword() {
            return clearTextPassword;
        }

        public String bcryptPassword() {
            return bcryptPassword;
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