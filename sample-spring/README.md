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
