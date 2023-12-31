# Samples, with Spring Boot

This samples shows you how to use `testcontainers-dex` with Spring Boot. It requires
the `wf.garnier:spring-boot-testcontainers-dex:3.2.0` dependency.

## Running the app

You can run the base project with `./gradlew :sample-spring:bootRun` from the root directory.
Make sure you have docker running.

Once the project runs, you can connect to the running app on http://localhost:8080/. It will
redirect you to Dex for log in. You can log in with `admin@example.com` / `password`, as defined
in the Dex config in `dex.yaml`.

## Tests

There are three test files.

- `ServiceConnectionTest` showcases the recommended way of using `@SpringBootTest` + `@TestContainer`, using
  the `@ServiceConnection` annotation. This leverages the auto-configuration present
  in `wf.garnier:spring-boot-testcontainers-dex`.

The other two test files show how you can control the lifecycle of starting the app and registering an OpenID Client
with Dex. In both tests you'll notice that the Boot app depends on the running container, so it is started first.
Then the container is updated to add an OAuth2 Client, that matches Boot's randomly selected port.

- `AutoLifecycleTest`: a test with `@SpringBootTest` and `@TestContainer`, but no `@ServiceConnection`
- `ManualTest`: a test with manual lifecycle management, calling `DexContainer#start()`
  and `SpringApplicationBuilder#run()` methods

