# Samples, with Spring Boot

This samples shows you how to use `testcontainers-dex` with Spring Boot.

## Running the app

You can run the base project with `./gradlew :sample-spring:bootRun` from the root directory.
Make sure you have docker running.

Once the project runs, you can connect to the running app on http://localhost:8080/. It will
redirect you to Dex for log in. You can log in with `admin@example.com` / `password`, as defined
in the Dex config in `dex.yaml`.

## Tests

There are two tests files:

- `AutoLifecycleTest`: a test with `@SpringBootTest` and `@TestContainer`
- `ManualTest`: a test with manual lifecycle management, calling `.start()` and `.run()` methods

In both tests you'll notice that the Boot app depends on the running container, and that the container
needs to know the Boot app's port before starting.

That's because Boot needs to know Dex's `issuer` before being able to start, and that URI needs to be
reachable over HTTP. So Dex must start first. Additionally, currently the `DexContainer` cannot dynamically
register clients, so the `redirect_uri` of the Boot app needs to be known in advance, before starting Dex.
We solve this issue by hardcoding Boot's port (to `1234` in these examples). That's not ideal for now.