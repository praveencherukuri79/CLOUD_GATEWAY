# How the Cloud Gateway Works

This document explains the current BFF/gateway application in detail.

## 1. What this application is

The gateway is a Spring Boot MVC application that acts as a Backend-for-Frontend (BFF).

Its main jobs are:

- accept browser requests
- authenticate the browser user with Spring Security session login
- route selected requests to downstream services
- create an internal JWT for service-to-service trust
- remove browser cookies before forwarding
- attach tracing headers like `X-APP-User` and `X-Correlation-Id`

The gateway runs on port `8080`.

Current config is in:

- `src/main/resources/application.yml`

Important values:

- gateway port: `8080`
- users downstream: `http://localhost:8081`
- orders downstream: `http://localhost:8082`
- JWT issuer: `bff-service`
- JWT audience: `internal-api`

## 2. Main request flow

A proxied request usually follows this path:

1. Browser calls the gateway, for example `GET /proxy/users/101`
2. Spring Security checks whether the browser already has a valid session
3. If the user is authenticated, the gateway route matches `/proxy/users/**`
4. A before-filter creates an internal JWT signed with the BFF private key
5. The gateway removes browser cookies from the forwarded request
6. The gateway adds:
   - `Authorization: Bearer <internal-jwt>`
   - `X-APP-User: <logged-in-user>`
   - `X-Correlation-Id: <trace-id>`
7. The gateway strips the `/proxy` prefix and forwards the request to the downstream base URL
8. The downstream service validates the JWT and returns a response
9. The gateway sends the downstream response back to the browser

## 3. Route configuration

Routing is configured in:

- `src/main/java/com/example/bff/config/GatewayRoutesConfig.java`

The app uses Spring Cloud Gateway Server WebMVC functional routes.

### 3.1 Users route

The users route matches:

- `/proxy/users/**`

Then it applies these steps in order:

1. `internalJwtRelayFilter.asBeforeFunction()`
2. `stripPrefix(1)`
3. `uri(usersServiceUri)`
4. response decoration via `proxyResponseHeadersFilter`

Because `stripPrefix(1)` removes only the first path segment, this happens:

- incoming: `/proxy/users/101`
- forwarded path: `/users/101`

Since `usersServiceUri` is `http://localhost:8081`, the final downstream target becomes:

- `http://localhost:8081/users/101`

### 3.2 Orders route

The orders route works the same way for:

- `/proxy/orders/**`

Example:

- incoming: `/proxy/orders/501`
- forwarded to: `http://localhost:8082/orders/501`

## 4. Authentication model

The browser does not call downstream services directly.

Instead:

- browser authenticates with the BFF
- BFF holds browser login state in session
- BFF generates a new internal JWT for downstream trust

This separates:

- browser authentication
- internal service authentication

That is a common BFF pattern.

## 5. Spring Security at the gateway

Security config lives in:

- `src/main/java/com/example/bff/config/SecurityConfig.java`

The gateway security layer is responsible for:

- login page / form login
- session handling for browser users
- restricting protected endpoints to authenticated users

So the browser talks to the gateway using session auth, not by presenting the internal JWT.

## 6. Internal JWT creation

The internal JWT is created in:

- `src/main/java/com/example/bff/service/InternalJwtService.java`

### 6.1 What it loads

At startup, the service loads the BFF private key from a configured PEM string.

Configured property:

- `app.internal-jwt.private-key`

The value is a PEM private key string stored directly in configuration.

### 6.2 What claims are created

For each authenticated proxied request, the gateway builds a JWT with:

- `iss` = `bff-service`
- `sub` = logged-in username
- `aud` contains `internal-api`
- `iat` = current time
- `exp` = current time + configured TTL
- custom `roles` claim from Spring Security authorities

### 6.3 How it is signed

The gateway signs the token with:

- RSA private key
- algorithm: `RS256`

This means downstream services only need the public key to verify the signature.

## 7. The relay filter

JWT relay logic lives in:

- `src/main/java/com/example/bff/filter/InternalJwtRelayFilter.java`

This is a gateway before-filter.

### 7.1 What it checks

It reads `Authentication` from `SecurityContextHolder` and rejects the request if:

- authentication is missing
- user is anonymous
- user is not authenticated

In that case it throws a proxy exception with HTTP `401`.

### 7.2 What it adds

If a user is authenticated, it creates the JWT and sets headers on the forwarded request:

- removes `Cookie`
- sets `Authorization: Bearer <jwt>`
- sets `X-APP-User`
- sets `X-Correlation-Id`

### 7.3 Why cookies are removed

Browser cookies like `JSESSIONID` are for the gateway only.

They should not be leaked to internal downstream services.

So the gateway converts browser session identity into a service JWT and forwards only the JWT.

## 8. Correlation and tracing headers

The gateway uses:

- `X-APP-User`
- `X-Correlation-Id`

Purpose:

- `X-APP-User` tells downstream who the gateway authenticated
- `X-Correlation-Id` helps trace a request across services

If a request does not already contain a correlation id, the gateway generates one.

## 9. Response decoration

Response header decoration is handled by:

- `src/main/java/com/example/bff/filter/ProxyResponseHeadersFilter.java`

This keeps gateway metadata on the response so the browser or developer can see which route handled the request.

## 10. Why Spring Cloud Gateway MVC is used here

This project uses:

- Spring Cloud Gateway Server WebMVC

instead of the reactive WebFlux variant.

That means:

- it fits a Spring MVC servlet application
- routes are declared using functional MVC gateway APIs
- request forwarding still behaves like a gateway, but in the servlet stack

## 11. Example end-to-end users request

Assume the user is already logged into the BFF.

Browser request:

- `GET http://localhost:8080/proxy/users/101`

Gateway behavior:

- matches `/proxy/users/**`
- gets authenticated session user, for example `demoUser123`
- signs internal JWT with private key
- strips `/proxy`
- forwards to `http://localhost:8081/users/101`

Forwarded headers include:

- `Authorization: Bearer <signed-rs256-jwt>`
- `X-APP-User: demoUser123`
- `X-Correlation-Id: <generated-or-forwarded-id>`

Users service then:

- verifies the JWT signature using the configured public key
- checks `iss = bff-service`
- checks `aud` contains `internal-api`
- reads `roles`
- returns the user response

## 12. Why this design is useful

This design gives a few practical benefits:

### 12.1 Browser stays simple

The browser only deals with gateway login/session.

It does not need to know internal service addresses or internal keys.

### 12.2 Downstream services trust the gateway

Downstream services do not trust browser cookies.

They only trust JWTs signed by the BFF private key.

### 12.3 Clear separation of concerns

- gateway handles browser/session/security boundary
- users service handles token verification and domain logic
- downstreams do not need direct browser authentication logic

### 12.4 Easier service isolation

Each downstream can independently verify the JWT using only the public key.

That is safer than sharing one secret everywhere.

## 13. Important files summary

Gateway behavior is mainly defined in:

- `src/main/java/com/example/bff/config/GatewayRoutesConfig.java`
- `src/main/java/com/example/bff/filter/InternalJwtRelayFilter.java`
- `src/main/java/com/example/bff/service/InternalJwtService.java`
- `src/main/resources/application.yml`

## 14. Common debugging checks

If proxying fails, check these first:

### 14.1 Route match

Does the request path start with:

- `/proxy/users/`
- `/proxy/orders/`

### 14.2 Downstream URL

Does the downstream base URL in config point to the right port?

### 14.3 Private key string

Does `app.internal-jwt.private-key` contain a valid PEM private key?

### 14.4 Public key match

Does the users service `app.security.jwt.public-key` come from the same keypair as the BFF private key?

If not, JWT validation will fail.

## 15. Mental model in one sentence

The gateway logs in the browser user once, then converts that identity into a short-lived RSA-signed internal JWT whenever it forwards a request to a downstream service.
