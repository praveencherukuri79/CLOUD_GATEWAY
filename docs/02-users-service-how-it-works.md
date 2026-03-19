# How the Users Service Works

This document explains the `users-service` application in detail.

## 1. What this service is

The users service is a Spring Boot microservice that runs on port `8081`.

Its main jobs are:

- receive requests forwarded from the BFF gateway
- validate the gateway-issued JWT using a configured RSA public key string
- reject invalid or missing JWTs
- expose a simple users endpoint
- return decoded JWT details for testing and verification

Current config is in:

- `users-service/src/main/resources/application.yml`

Key values:

- port: `8081`
- public key: `app.security.jwt.public-key`
- expected issuer: `bff-service`
- expected audience: `internal-api`

## 2. Why this service uses a public key

The BFF signs tokens with a private RSA key.

The users service only needs the matching public key to verify that:

- the token really came from the BFF
- the token was not tampered with

This is asymmetric cryptography.

### 2.1 Private key vs public key

- private key: secret, stays in the BFF
- public key: safe to distribute to downstream services

The users service must never need the private key.

## 3. High-level request flow

Example request chain:

1. browser calls `GET /proxy/users/101` on the gateway
2. gateway authenticates the browser session
3. gateway creates an internal JWT signed with its private key
4. gateway forwards request to `http://localhost:8081/users/101`
5. users service reads `Authorization: Bearer <jwt>`
6. Spring Security resource server support decodes and validates the JWT
7. users controller executes only if the JWT is valid
8. service returns data

## 4. Security configuration

Security setup is in:

- `users-service/src/main/java/com/example/userservice/config/SecurityConfig.java`

This class is intentionally thin.

It mainly does three things:

1. defines the `SecurityFilterChain`
2. exposes a `JwtDecoder` bean
3. exposes a `JwtAuthenticationConverter` bean

The actual JWT-specific helper logic is delegated to:

- `users-service/src/main/java/com/example/userservice/util/JwtSecurityUtils.java`

## 5. SecurityFilterChain behavior

The filter chain currently does the following:

### 5.1 Disables CSRF

CSRF is mainly relevant for browser form/session flows.

This service behaves like a stateless API receiving Bearer tokens, so disabling CSRF is normal here.

### 5.2 Uses stateless sessions

The service sets:

- `SessionCreationPolicy.STATELESS`

Meaning:

- it does not create login sessions
- every request must carry its own JWT
- authentication is evaluated independently on each request

### 5.3 Public endpoint

This endpoint is open:

- `GET /public/ping`

Useful for basic health-style checks.

### 5.4 Protected endpoints

All other endpoints require authentication.

In practice, that means they require a valid Bearer JWT.

## 6. Why `oauth2ResourceServer().jwt(...)` is used

Even though the JWT is custom, Spring Security's resource-server support is still the correct JWT validation feature.

It gives you:

- Bearer token extraction
- JWT parsing
- signature validation
- expiry validation
- issuer validation
- creation of authenticated principal
- integration with Spring Security authorization

It does **not** require an external OAuth provider.

Here it is only being used as the JWT validation pipeline.

## 7. JWT decode and validation logic

JWT helper logic lives in:

- `users-service/src/main/java/com/example/userservice/util/JwtSecurityUtils.java`

That file has two main responsibilities:

- build the `JwtDecoder`
- build the `JwtAuthenticationConverter`

### 7.1 Public key loading

The helper reads the public key from:

- `app.security.jwt.public-key`

The value is a PEM public key string.

The code strips the PEM markers, Base64-decodes the content, and builds:

- `RSAPublicKey`

This public key is then used by `NimbusJwtDecoder`.

### 7.2 What `NimbusJwtDecoder` does

`NimbusJwtDecoder` is Spring Security's JWT decoder implementation backed by Nimbus JOSE JWT.

It is responsible for:

- parsing the Bearer token
- verifying the RSA signature
- converting claims into a `Jwt` object
- enforcing configured validators

### 7.3 Signature verification

The decoder is built like this conceptually:

- `NimbusJwtDecoder.withPublicKey(rsaPublicKey).build()`

That means the service trusts tokens signed by the matching private key only.

If someone sends:

- a modified token
- a token signed by another private key
- a malformed token

validation fails and the request is rejected.

### 7.4 Issuer validation

The helper configures a default issuer validator using:

- `JwtValidators.createDefaultWithIssuer(issuer)`

Expected issuer in config:

- `bff-service`

So if `iss` is anything else, the token is rejected.

### 7.5 Audience validation

The helper also adds audience validation for:

- `internal-api`

It checks that the JWT `aud` claim contains that value.

If `aud` is missing or does not include `internal-api`, validation fails.

### 7.6 Standard validations you get

By using Spring Security JWT validation, you also get standard checks like:

- expiration time (`exp`)
- not-before (`nbf`) when present
- malformed token detection

## 8. Public key details

The users service uses:

- PEM public key text in configuration

For JWT verification, it mainly cares about reconstructing the RSA public key from that string.

## 9. Authority mapping from JWT

The gateway includes a custom claim:

- `roles`

Example:

- `roles: ["ROLE_USER"]`

The users service maps that claim into Spring Security authorities using `JwtGrantedAuthoritiesConverter`.

Current behavior:

- claim name = `roles`
- authority prefix = empty string `""`

That means:

- `ROLE_USER` stays `ROLE_USER`
- no extra `SCOPE_` prefix is added

This is important because the token is using role-style authorities, not scope-style claims.

## 10. Authenticated principal inside controller

Controller logic is in:

- `users-service/src/main/java/com/example/userservice/controller/UsersController.java`

Protected endpoint:

- `GET /users/{userId}`

The controller receives the validated JWT via:

- `@AuthenticationPrincipal Jwt jwt`

That means by the time the controller runs:

- token signature is already verified
- issuer is already checked
- audience is already checked
- the request is already authenticated

The controller does not manually validate the token.

That is a good design.

## 11. What the controller returns

For testing, the controller returns a structured response containing:

- requested user id
- message
- JWT subject
- JWT issuer
- JWT audience
- roles from JWT
- `X-APP-User`
- `X-Correlation-Id`

This is helpful to confirm end-to-end behavior while developing.

## 12. Example token lifecycle

Assume the BFF generates this token payload conceptually:

- `iss = bff-service`
- `sub = demoUser123`
- `aud = [internal-api]`
- `roles = [ROLE_USER]`
- signed with BFF private key using `RS256`

When the users service receives it:

1. Spring extracts the Bearer token
2. `NimbusJwtDecoder` parses it
3. RSA signature is verified against the configured public key
4. issuer is checked
5. audience is checked
6. expiration is checked
7. `roles` claim is converted to Spring authorities
8. `Jwt` becomes the authenticated principal
9. controller executes

## 13. Why public-key validation is better here than shared secret

Using RSA keys has a strong benefit for multi-service systems.

With symmetric HMAC:

- every validating service needs the same secret
- any service with the secret could also mint tokens

With asymmetric RSA:

- only the BFF keeps the private key
- downstream services only get the public key
- downstream services can verify tokens but cannot create fake ones

That is safer for service-to-service trust boundaries.

## 14. Failure scenarios

The users service will reject requests in cases like:

### 14.1 Missing Authorization header

No Bearer token means no authenticated principal.

### 14.2 Invalid signature

If token signature does not match the configured public key, validation fails.

### 14.3 Wrong issuer

If `iss != bff-service`, validation fails.

### 14.4 Wrong audience

If `aud` does not include `internal-api`, validation fails.

### 14.5 Expired token

If `exp` is in the past, validation fails.

### 14.6 Wrong keypair pairing

If the BFF private key and the users service public key are not from the same keypair, every token verification will fail.

## 15. Important files summary

Core files for users-service JWT auth:

- `users-service/src/main/java/com/example/userservice/config/SecurityConfig.java`
- `users-service/src/main/java/com/example/userservice/util/JwtSecurityUtils.java`
- `users-service/src/main/java/com/example/userservice/controller/UsersController.java`
- `users-service/src/main/resources/application.yml`
- `users-service/src/main/resources/application.yml`

## 16. Mental model in one sentence

The users service is a stateless API that trusts only JWTs signed by the BFF private key and verified using the matching public key.
