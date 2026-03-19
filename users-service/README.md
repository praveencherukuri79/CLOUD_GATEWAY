# Users Service

Sample Spring Boot 3.5.6 microservice running on port `8081`.

Behavior:

- receives requests from the BFF route `/proxy/users/**`
- validates the JWT signature with the configured RSA public key string
- checks issuer `bff-service`
- checks audience `internal-api`

Run from workspace root:

- `mvn spring-boot:run -f users-service/pom.xml`

Example direct test:

- `GET http://localhost:8081/public/ping`
- `GET http://localhost:8081/users/101` with a Bearer token signed by the BFF private key