package wf.garnier.testcontainers.dexidp;


import java.time.Duration;
import java.util.Collections;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

// TODO:
// - get client
// - (test) get token
// - get user
// - (test) login
// - register users
// - register clients
// - use ephemeral ports in the issuer (pre-load script _inside_ the started container)

public class DexContainer extends GenericContainer<DexContainer> {

    private final int DEX_PORT = 5556;

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
        this.withCopyToContainer(Transferable.of(configuration()), "/etc/dex/dex.yml");
    }

    public String getIssuerUri() {
        return "http://%s:%s/dex".formatted(getHost(), DEX_PORT);
    }

    private String configuration() {
        // TODO: jackson-databind?
        return """
                issuer: %s
                storage:
                  type: sqlite3
                  config:
                    file: /etc/dex/dex.db
                web:
                  http: 0.0.0.0:%s
                staticClients:
                  - id: example-app
                    redirectURIs:
                      - 'http://127.0.0.1:5555/callback'
                    name: 'Example App'
                    secret: ZXhhbXBsZS1hcHAtc2VjcmV0
                enablePasswordDB: true
                staticPasswords:
                  - email: "admin@example.com"
                    hash: "$2a$10$2b2cU8CPhOTaGrs1HRQuAueS7JTT5ZHsHSzYiFPm1leZck7Mc8T4W"
                    username: "admin"
                    userID: "08a8684b-db88-4b73-90a9-3cd1661f5466"
                """.formatted(getIssuerUri(), DEX_PORT);
    }


}