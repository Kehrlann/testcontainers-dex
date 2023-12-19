# testcontainers-dex

A Testcontainers module for the [Dex OpenID Provider](https://dexidp.io), with
a simple interface to register Clients and Users.

Dex is lightweight IDP, written in Go, which boot fasts. It is less "feature-ful"
than the venerable Keycloak, but can be good enough to have a real "openid login".

Dex is designed for federating login from upstream identity providers, but those
features are not used here.

## Download

Maven:

```xml

<dependencies>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>wf.garnier</groupId>
        <artifactId>testcontainers-dex</artifactId>
        <version>3.2.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Gradle:

```gradle
testImplementation("org.testcontainers:testcontainers:1.19.3")
testImplementation("wf.garnier:testcontainers-dex:3.2.0")
```

## Usage

The main class is `DexContainer`, which starts a Dex server on a randomly selected port,
with a default Client and user. The users may log in using their `email` and `clearTextPassword`.
Basic usage:

```java
class MyTests {
    @Test
    public void someTest() {
        try (var container = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG))) {
            container.start();
            var issuerUri = container.getIssuerUri();
            var config = DO_SOME_CONFIGURATION(issuerUri);
            var result = OBTAIN_TOKEN(
                    config,
                    container.getUser().email(),
                    container.getUser().clearTextPassword(),
                    container.getClient().clientId(),
                    container.getClient().clientSecret()
            );
            assertThat(result).satisfies(token -> { /* ... */ });
        }
    }

}
```

## Usage with Spring Boot

Check out the `sample-spring` directory in this project. In that case you want to pull in
the `wf.garnier:spring-boot-testcontainers-dex:3.2.0` dependency, rather than just the Dex module. This will allow you
to autoconfigure your application when running tests, and use the `@ServiceConnection` annotation.

Maven:

```xml

<dependencies>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>1.19.3</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>wf.garnier</groupId>
        <artifactId>spring-boot-testcontainers-dex</artifactId>
        <version>3.2.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Gradle:

```gradle
testImplementation("org.testcontainers:testcontainers:1.19.3")
testImplementation("wf.garnier:spring-boot-testcontainers-dex:3.2.0")
```

Using with Spring Boot:

```java
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
```

## Detailed usage

To dynamically obtain configuration data, use the `issuerUri` property and get
`<issuer-uri>/.well-known/openid-configuration`. Learn more in the
[OpenID Discovery spec](https://openid.net/specs/openid-connect-discovery-1_0.html):

```java
class MyTests {
    public void someTest() {
        try (var container = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG))) {
            container.start();
            var issuerUri = container.getIssuerUri();
            var openidConfigurationUri = URI.create(issuerUri + "/.well-known/openid-configuration");
            var request = HttpRequest.newBuilder(openidConfigurationUri)
                    .GET()
                    .build();
            var httpResponse = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString())
                    .body();
        }
    }
}
```

## Development, contributing

Roadmap is currently tracked in `roadmap.md`. For non-trivial PRs, please open an issue to discuss
the proposed API or behavior changes before submitting code.