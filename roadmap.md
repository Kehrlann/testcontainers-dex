# Roadmap

This represents a rough roadmap for the current project. The order is generally representative of intent,
but there is not guarantee on the order.

Feel free to open an issue if you would like to improve the project and you see something missing here!

- [x] Add a logger in the tests of the library
- [x] Add a Spring Boot sample
- [x] Write documentation
- [ ] Add a way to get openid configuration data without using the OpenID Discovery mechanism
- [ ] Add a test for the userinfo endpoint
- [x] Use the ephemeral port for the container in the issuer-uri
- [ ] Add dynamic client provisioning
- [ ] Add dynamic user provisioning
- [ ] Test docker-compose support

## Infrastructure-related

- [ ] Improve publish scripts to use PGP without running extra CLI commands
- [ ] Github actions tests
- [ ] Formatting?

## Probably not supported

- Use a fixed port for the container
