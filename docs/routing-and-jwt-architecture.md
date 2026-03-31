# BFF Gateway — Routing & JWT Architecture

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Service Port Map](#2-service-port-map)
3. [Two Routing Approaches](#3-two-routing-approaches)
4. [Gateway Routes (Spring Cloud Gateway MVC)](#4-gateway-routes-spring-cloud-gateway-mvc)
5. [Custom Proxy Routes (RestClient-Based)](#5-custom-proxy-routes-restclient-based)
6. [Gateway vs Custom Proxy — Comparison](#6-gateway-vs-custom-proxy--comparison)
7. [Internal JWT — Signing & Structure](#7-internal-jwt--signing--structure)
8. [JWKS Endpoint & Key Management](#8-jwks-endpoint--key-management)
9. [Downstream JWT Validation (Users Service)](#9-downstream-jwt-validation-users-service)
10. [Authentication & Role-Based Permission Flow](#10-authentication--role-based-permission-flow)
11. [Session Management](#11-session-management)
12. [Request Headers & Correlation](#12-request-headers--correlation)
13. [Error Handling](#13-error-handling)
14. [Configuration Reference](#14-configuration-reference)

---

## 1. System Overview

```mermaid
architecture-beta
    group bff(cloud)[BFF Gateway - port 8080]
    group downstream(cloud)[Downstream Services]
    group infra(cloud)[Infrastructure]

    service gwRoutes(server)[Gateway Routes] in bff
    service customProxy(server)[Custom Proxy] in bff
    service jwtSigner(disk)[JWT Signer] in bff
    service jwksEp(internet)[JWKS Endpoint] in bff

    service users(server)[Users - 8081] in downstream
    service orders(server)[Orders - 8082] in downstream
    service notifications(server)[Notifications - 8083] in downstream
    service alerts(server)[Alerts - 8084] in downstream

    service configSvc(database)[Config Service - 8085] in infra

    gwRoutes:R -- L:users
    gwRoutes:R -- L:orders
    customProxy:R -- L:notifications
    customProxy:R -- L:alerts
    jwtSigner:B -- T:gwRoutes
    jwtSigner:B -- T:customProxy
    jwksEp:R -- L:users
```

The BFF Gateway is the single entry point for browser clients. It authenticates users via form login, manages sessions and roles, then proxies requests to downstream microservices. Every proxied request carries a **signed internal JWT** instead of session cookies.

---

## 2. Service Port Map

| Service            | Port   | Purpose                                    |
|--------------------|--------|--------------------------------------------|
| BFF Gateway        | `8080` | Authentication, routing, JWT signing       |
| Users Service      | `8081` | User data (real Spring Boot service)       |
| Orders Service     | `8082` | Order data (mock)                          |
| Notifications      | `8083` | Notifications (mock, custom proxy)         |
| Alerts             | `8084` | Alerts (mock, custom proxy)                |
| Config Service     | `8085` | Role permission configuration (mock)       |

---

## 3. Two Routing Approaches

The BFF implements **two distinct routing mechanisms** side by side:

```mermaid
flowchart LR
    Browser["Browser Client"]

    subgraph BFF["BFF Gateway :8080"]
        direction TB
        GW["Gateway Routes<br/>/proxy/**"]
        CP["Custom Proxy<br/>/custom-proxy/**"]
    end

    subgraph DS["Downstream"]
        Users["Users :8081"]
        Orders["Orders :8082"]
        Notifications["Notifications :8083"]
        Alerts["Alerts :8084"]
    end

    Browser -->|"/proxy/users/**"| GW
    Browser -->|"/proxy/orders/**"| GW
    Browser -->|"/custom-proxy/notifications/**"| CP
    Browser -->|"/custom-proxy/alerts/**"| CP

    GW -->|"Bearer JWT"| Users
    GW -->|"Bearer JWT"| Orders
    CP -->|"Bearer JWT"| Notifications
    CP -->|"Bearer JWT"| Alerts
```

| Aspect               | Gateway Routes (`/proxy/**`)                             | Custom Proxy (`/custom-proxy/**`)                       |
|----------------------|----------------------------------------------------------|---------------------------------------------------------|
| Implementation       | Spring Cloud Gateway MVC `RouterFunction`                | `@RestController` + `RestClient`                        |
| Defined in           | `GatewayRoutesConfig`                                    | `CustomProxyController` + `CustomProxyService`          |
| Filter mechanism     | `before()`/`after()` filter functions                    | Manual header building in service layer                 |
| Services routed      | Users, Orders                                            | Notifications, Alerts                                   |
| Path stripping       | Automatic via `stripPrefix(1)`                           | Manual via `CustomProxyUtils.buildTargetUri()`          |
| JWT injection        | `InternalJwtRelayFilter`                                 | `CustomProxyService` calls `InternalJwtService` directly|
| Error handling       | Gateway framework + `GlobalExceptionHandler`             | Try/catch in `CustomProxyService` + `GlobalExceptionHandler` |

Both approaches produce the **same outbound request** to downstream services: a request with `Authorization: Bearer <jwt>`, `X-APP-User`, and `X-Correlation-Id` headers.

---

## 4. Gateway Routes (Spring Cloud Gateway MVC)

### Route Registration

Routes are defined as `RouterFunction` beans in `GatewayRoutesConfig`:

```java
route("users-service-route")
    .route(RequestPredicates.path("/proxy/users/**"), http())
    .before(internalJwtRelayFilter.asBeforeFunction())   // inject JWT
    .before(stripPrefix(1))                               // /proxy/users/123 → /users/123
    .before(uri(usersServiceUri))                         // set target base URI
    .after(proxyResponseHeadersFilter.asAfterFunction("users-service-route"))
    .build();
```

### Request Processing Pipeline

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant SecurityFilter as Spring Security
    participant GW as Gateway Router
    participant JwtFilter as InternalJwtRelayFilter
    participant StripPrefix as stripPrefix(1)
    participant UriFilter as uri(target)
    participant DS as Downstream Service
    participant RespFilter as ProxyResponseHeadersFilter

    Browser->>SecurityFilter: GET /proxy/users/123 (Cookie: JSESSIONID)
    SecurityFilter->>SecurityFilter: Validate session, load Authentication
    SecurityFilter->>GW: Authenticated request

    GW->>JwtFilter: Before filter 1
    Note over JwtFilter: Read Authentication from SecurityContext<br/>Read ACTIVE_ROLE from HttpSession<br/>Sign JWT with RSA private key
    JwtFilter->>JwtFilter: Remove Cookie header<br/>Set Bearer token<br/>Set X-APP-User<br/>Set X-Correlation-Id

    GW->>StripPrefix: Before filter 2
    Note over StripPrefix: /proxy/users/123 becomes /users/123

    GW->>UriFilter: Before filter 3
    Note over UriFilter: Set target to http://localhost:8081

    GW->>DS: GET http://localhost:8081/users/123<br/>Authorization: Bearer jwt<br/>X-APP-User: demoUser123<br/>X-Correlation-Id: uuid

    DS-->>GW: 200 OK + JSON body

    GW->>RespFilter: After filter
    Note over RespFilter: Add X-Correlation-Id<br/>Add X-APP-User<br/>Add X-Gateway-Route

    GW-->>Browser: 200 OK + response headers
```

### InternalJwtRelayFilter Responsibilities

1. **Authentication check** — rejects unauthenticated/anonymous requests with `401`
2. **Session read** — reads `ACTIVE_ROLE` from `HttpSession`
3. **JWT creation** — calls `InternalJwtService.createToken(auth, clientIp, activeRole)`
4. **Header replacement** — removes `Cookie`, sets `Authorization: Bearer <jwt>`, `X-APP-User`, `X-Correlation-Id`

### ProxyResponseHeadersFilter Responsibilities

Adds traceability headers to downstream responses before returning to browser:
- `X-Correlation-Id`
- `X-APP-User`
- `X-Gateway-Route` (identifies which route was used)

---

## 5. Custom Proxy Routes (RestClient-Based)

### Architecture

```mermaid
flowchart TB
    subgraph Controller["CustomProxyController"]
        NRoute["@RequestMapping /custom-proxy/notifications/**"]
        ARoute["@RequestMapping /custom-proxy/alerts/**"]
    end

    subgraph Service["CustomProxyService"]
        BuildURI["Build target URI"]
        BuildHeaders["Build outbound headers"]
        SignJWT["Sign internal JWT"]
        Execute["Execute RestClient call"]
        HandleErr["Handle errors"]
    end

    subgraph Utils["CustomProxyUtils"]
        ResolveCorrelation["Resolve correlation ID"]
        TargetURI["Strip prefix, build target"]
        OutboundHeaders["Copy + modify headers"]
    end

    NRoute --> Service
    ARoute --> Service
    Service --> BuildURI --> TargetURI
    Service --> BuildHeaders --> OutboundHeaders
    Service --> SignJWT
    Service --> Execute
    Execute --> HandleErr
```

### Request Flow

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant Controller as CustomProxyController
    participant Service as CustomProxyService
    participant JwtService as InternalJwtService
    participant Utils as CustomProxyUtils
    participant DS as Downstream Service

    Browser->>Controller: GET /custom-proxy/notifications/42

    Controller->>Service: forward(request, body, prefix, targetUri, routeId)

    Service->>Utils: resolveCorrelationId(request)
    Utils-->>Service: correlationId (from header or new UUID)

    Service->>Utils: buildTargetUri(request, "/custom-proxy/notifications", "http://localhost:8083")
    Utils-->>Service: http://localhost:8083/notifications/42

    Service->>JwtService: createToken(auth, clientIp, activeRole)
    JwtService-->>Service: signed JWT string

    Service->>Utils: buildOutboundHeaders(request, appUser, correlationId, token)
    Note over Utils: Copy original headers<br/>Remove Cookie, Host, Authorization, Content-Length<br/>Set Bearer, X-APP-User, X-Correlation-Id
    Utils-->>Service: HttpHeaders

    Service->>DS: GET http://localhost:8083/notifications/42<br/>Authorization: Bearer jwt

    alt Success
        DS-->>Service: 200 OK + body
        Service-->>Controller: ResponseEntity with proxy headers
    else Downstream error (4xx/5xx)
        DS-->>Service: Error response
        Service-->>Controller: ResponseEntity preserving status + body
    else Connection failure
        Service-->>Controller: ProxyRequestException (502 BAD_GATEWAY)
    end

    Controller-->>Browser: Response with X-Correlation-Id, X-APP-User, X-Custom-Route
```

### Path Transformation

```
Request:  /custom-proxy/notifications/42?page=1
Prefix:   /custom-proxy/notifications
Target:   http://localhost:8083

Result:   http://localhost:8083/42?page=1
```

The `CustomProxyUtils.buildTargetUri()` method:
1. Strips the context path (if any)
2. Removes the route prefix from the request path
3. Appends the remaining path and query string to the target base URI

---

## 6. Gateway vs Custom Proxy — Comparison

```mermaid
flowchart TB
    subgraph GW["Gateway Route Approach"]
        direction LR
        GW1["Declarative route definition"]
        GW2["Built-in filter chain"]
        GW3["Automatic path/URI handling"]
        GW4["Framework error handling"]
    end

    subgraph CP["Custom Proxy Approach"]
        direction LR
        CP1["Imperative controller code"]
        CP2["Manual header management"]
        CP3["Custom path stripping logic"]
        CP4["Full control over error responses"]
    end
```

| When to use Gateway Routes           | When to use Custom Proxy                    |
|---------------------------------------|---------------------------------------------|
| Simple pass-through proxying          | Complex request/response transformation     |
| Standard filter chains are sufficient | Need to inspect or modify the body          |
| Want minimal boilerplate              | Need fine-grained error handling per route  |
| Upstream follows REST conventions     | Custom business logic during proxying       |

---

## 7. Internal JWT — Signing & Structure

### JWT Creation Flow

```mermaid
sequenceDiagram
    autonumber
    participant Caller as Filter / ProxyService
    participant JwtSvc as InternalJwtService
    participant KeyProv as JwksKeyProvider

    Caller->>JwtSvc: createToken(authentication, clientIp, activeRole)

    JwtSvc->>JwtSvc: Build JWTClaimsSet
    Note over JwtSvc: iss: "bff-service"<br/>sub: username<br/>aud: ["internal-api"]<br/>iat/nbf: now<br/>exp: now + 300s<br/>jti: random UUID<br/>roles: [authorities]<br/>activeRole: selected role<br/>username: display name<br/>ipAddress: client IP

    JwtSvc->>KeyProv: getPrivateKey()
    KeyProv-->>JwtSvc: RSA private key

    JwtSvc->>JwtSvc: Sign with RS256 (RSASSASigner)

    JwtSvc-->>Caller: Serialized JWT string
```

### JWT Claims Structure

```json
{
  "iss": "bff-service",
  "sub": "demoUser123",
  "aud": ["internal-api"],
  "iat": 1743350400,
  "nbf": 1743350400,
  "exp": 1743350700,
  "jti": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "roles": ["ROLE_USER", "dashboard.view", "orders.view", "orders.create"],
  "activeRole": "ROLE_USER",
  "username": "demoUser123",
  "ipAddress": "127.0.0.1"
}
```

### Key Properties

| Property     | Value                     | Purpose                                         |
|--------------|---------------------------|-------------------------------------------------|
| Algorithm    | RS256                     | RSA signature with SHA-256                      |
| Key ID (kid) | SHA-256 thumbprint        | Allows downstream JWKS key lookup               |
| TTL          | 300 seconds (5 min)       | Short-lived, one per proxied request            |
| Issuer       | `bff-service`             | Identifies the BFF as token source              |
| Audience     | `internal-api`            | Restricts token to internal services            |

### What Goes Into the `roles` Claim

The `roles` claim contains **all authorities** from the Spring Security `Authentication` object, which includes:
- Original role authorities (e.g. `ROLE_USER`, `ROLE_ADMIN`)
- Feature permission authorities added by `SecurityContextHelper` (e.g. `dashboard.view`, `orders.create`)

---

## 8. JWKS Endpoint & Key Management

```mermaid
sequenceDiagram
    autonumber
    participant App as BFF Startup
    participant KP as JwksKeyProvider
    participant PEM as classpath:keys/bff-jwt-private.pem

    App->>KP: @PostConstruct init()
    KP->>PEM: Load private key PEM
    PEM-->>KP: RSA private key bytes
    KP->>KP: Parse PKCS8 to RSAPrivateKey
    KP->>KP: Derive RSAPublicKey from CRT parameters
    KP->>KP: Generate key ID (SHA-256 thumbprint)
    KP->>KP: Build JWKSet with public key

    Note over KP: Ready to serve JWKS and sign JWTs
```

### JWKS Endpoint

**URL:** `GET /.well-known/jwks.json` (public, no authentication required)

**Response:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "<sha256-thumbprint>",
      "n": "<modulus>",
      "e": "AQAB"
    }
  ]
}
```

Downstream services (like Users Service) fetch this endpoint to obtain the public key for JWT signature verification.

### Key Lifecycle

```mermaid
flowchart LR
    PEM["PEM File<br/>(classpath:keys/)"] -->|"Load at startup"| KP["JwksKeyProvider"]
    KP -->|"Private key"| Sign["InternalJwtService<br/>Signs JWTs"]
    KP -->|"Public key as JWKSet"| JWKS["/.well-known/jwks.json"]
    JWKS -->|"Downstream fetches"| Verify["Users Service<br/>NimbusJwtDecoder"]
```

---

## 9. Downstream JWT Validation (Users Service)

```mermaid
sequenceDiagram
    autonumber
    participant BFF as BFF Gateway
    participant US as Users Service
    participant Decoder as NimbusJwtDecoder
    participant JWKS as BFF JWKS Endpoint
    participant ConfigSvc as Config Service
    participant Controller as UsersController

    BFF->>US: GET /users/123<br/>Authorization: Bearer jwt<br/>X-APP-User: demoUser123

    US->>Decoder: Decode & validate JWT
    Decoder->>JWKS: Fetch public key<br/>GET http://localhost:8080/.well-known/jwks.json
    JWKS-->>Decoder: JWK Set (RSA public key)
    Decoder->>Decoder: Verify signature (RS256)
    Decoder->>Decoder: Validate issuer = "bff-service"
    Decoder->>Decoder: Validate audience contains "internal-api"
    Decoder->>Decoder: Check expiration

    US->>US: JwtAuthenticationConverter.buildAuthorities()
    Note over US: 1. Extract "roles" claim as authorities<br/>2. Read "activeRole" claim<br/>3. Fetch permissions from Config Service

    US->>ConfigSvc: GET /api/config/permissions/ROLE_USER
    ConfigSvc-->>US: RolePermissions (features map)

    Note over US: Flatten enabled permissions:<br/>dashboard.view, orders.view, orders.create

    US->>Controller: @PreAuthorize("hasAuthority('users.view')")
    
    alt Has authority
        Controller-->>BFF: 200 OK + user data
    else Missing authority
        Controller-->>BFF: 403 Forbidden
    end
```

### Validation Steps

1. **Signature verification** — using public key fetched from BFF's JWKS endpoint
2. **Issuer validation** — must be `bff-service`
3. **Audience validation** — must contain `internal-api`
4. **Expiration check** — standard JWT `exp` check
5. **Authority building** — `JwtAuthenticationConverter` builds `GrantedAuthority` list from JWT claims + config-service permissions

### Authority Building in Users Service

The `buildAuthorities` method in the Users Service `SecurityConfig`:

```
JWT roles claim → SimpleGrantedAuthority per role
    e.g. ROLE_USER, ROLE_ADMIN

activeRole claim → fetch from config-service → flatten enabled permissions
    e.g. dashboard.view, orders.view, orders.create, users.view
```

These authorities are then checked by `@PreAuthorize` annotations on controller methods.

---

## 10. Authentication & Role-Based Permission Flow

### Single-Role User Login

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant Security as Spring Security
    participant Handler as RoleAwareLoginSuccessHandler
    participant PermSvc as RolePermissionService
    participant ConfigSvc as Config Service :8085
    participant Helper as SecurityContextHelper
    participant Session as HttpSession

    Browser->>Security: POST /login (username=demoUser123, password=password)
    Security->>Security: Authenticate (InMemoryUserDetailsManager)
    Security->>Handler: onAuthenticationSuccess(authentication)

    Handler->>Handler: Extract roles from authorities
    Note over Handler: roles = ["ROLE_USER"] (single role)

    Handler->>PermSvc: fetchPermissions("ROLE_USER")
    PermSvc->>ConfigSvc: GET /api/config/permissions/ROLE_USER
    ConfigSvc-->>PermSvc: RolePermissions JSON
    PermSvc-->>Handler: RolePermissions object

    Handler->>Session: Set ACTIVE_ROLE = "ROLE_USER"
    Handler->>Session: Set ROLE_PERMISSIONS = RolePermissions
    Handler->>Session: Remove ROLE_SELECTION_REQUIRED

    Handler->>Helper: applyPermissionAuthorities(auth, permissions)
    Note over Helper: Add enabled permissions as authorities:<br/>dashboard.view, orders.view, orders.create
    Helper->>Helper: Replace Authentication in SecurityContext

    Handler->>Browser: Redirect to saved request (or default)
```

### Multi-Role User Login

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant Handler as RoleAwareLoginSuccessHandler
    participant Session as HttpSession
    participant RoleCtrl as RoleController
    participant PermSvc as RolePermissionService
    participant ConfigSvc as Config Service
    participant Helper as SecurityContextHelper

    Browser->>Handler: Login as adminUser (ROLE_USER + ROLE_ADMIN)
    Handler->>Handler: roles.size() > 1
    Handler->>Session: Set ROLE_SELECTION_REQUIRED = true
    Handler->>Browser: Redirect to /role-selection

    Browser->>RoleCtrl: GET /api/role/available
    RoleCtrl-->>Browser: roles: [ROLE_USER, ROLE_ADMIN], selectionRequired: true

    Browser->>RoleCtrl: POST /api/role/select {role: "ROLE_ADMIN"}
    RoleCtrl->>RoleCtrl: Validate user has ROLE_ADMIN authority

    RoleCtrl->>PermSvc: fetchPermissions("ROLE_ADMIN")
    PermSvc->>ConfigSvc: GET /api/config/permissions/ROLE_ADMIN
    ConfigSvc-->>PermSvc: RolePermissions (all features enabled)
    PermSvc-->>RoleCtrl: RolePermissions object

    RoleCtrl->>Session: Set ACTIVE_ROLE = "ROLE_ADMIN"
    RoleCtrl->>Session: Set ROLE_PERMISSIONS = RolePermissions
    RoleCtrl->>Session: Remove ROLE_SELECTION_REQUIRED

    RoleCtrl->>Helper: applyPermissionAuthorities(auth, permissions)
    Note over Helper: Adds: dashboard.view, dashboard.edit,<br/>orders.view, orders.edit, orders.create, orders.delete,<br/>users.view, users.edit, users.create, users.delete

    RoleCtrl-->>Browser: {activeRole: "ROLE_ADMIN", features: {...}}
```

### Permission Structure from Config Service

```json
{
  "role": "ROLE_ADMIN",
  "features": {
    "dashboard": { "view": true, "edit": true },
    "orders":    { "view": true, "edit": true, "create": true, "delete": true },
    "users":     { "view": true, "edit": true, "create": true, "delete": true }
  }
}
```

**Flattened to authorities:** `dashboard.view`, `dashboard.edit`, `orders.view`, `orders.edit`, `orders.create`, `orders.delete`, `users.view`, `users.edit`, `users.create`, `users.delete`

Only permissions with `value = true` are added as authorities.

---

## 11. Session Management

### Session Attributes

| Key                      | Type               | Set When                        | Purpose                              |
|--------------------------|--------------------|---------------------------------|--------------------------------------|
| `ACTIVE_ROLE`            | `String`           | Login (single role) or `/api/role/select` | Currently active role         |
| `ROLE_PERMISSIONS`       | `RolePermissions`  | Same as above                   | Full permission map for active role  |
| `ROLE_SELECTION_REQUIRED`| `Boolean`          | Login (multi-role)              | Signals frontend to show role picker |

### Session Flow

```mermaid
flowchart TD
    Login["User Logs In"] --> Check{"Single role?"}

    Check -->|"Yes"| Auto["Auto-apply role"]
    Auto --> SetRole["Session: ACTIVE_ROLE"]
    Auto --> SetPerms["Session: ROLE_PERMISSIONS"]
    Auto --> AddAuth["SecurityContext: + permission authorities"]
    SetRole --> Ready["Ready to proxy"]
    SetPerms --> Ready
    AddAuth --> Ready

    Check -->|"No"| Flag["Session: ROLE_SELECTION_REQUIRED = true"]
    Flag --> Redirect["Redirect to /role-selection"]
    Redirect --> Select["POST /api/role/select"]
    Select --> SetRole2["Session: ACTIVE_ROLE"]
    Select --> SetPerms2["Session: ROLE_PERMISSIONS"]
    Select --> AddAuth2["SecurityContext: + permission authorities"]
    Select --> ClearFlag["Session: Remove ROLE_SELECTION_REQUIRED"]
    SetRole2 --> Ready2["Ready to proxy"]
    SetPerms2 --> Ready2
    AddAuth2 --> Ready2
    ClearFlag --> Ready2
```

---

## 12. Request Headers & Correlation

### Outbound Headers (BFF → Downstream)

| Header              | Value                          | Set By                          |
|---------------------|--------------------------------|---------------------------------|
| `Authorization`     | `Bearer <signed-jwt>`          | JWT relay filter / proxy service |
| `X-APP-User`        | Authenticated username          | JWT relay filter / proxy service |
| `X-Correlation-Id`  | UUID (from request or generated)| JWT relay filter / proxy service |

### Response Headers (Downstream → Browser)

| Header              | Value                          | Set By                          |
|---------------------|--------------------------------|---------------------------------|
| `X-Correlation-Id`  | Same as request                 | Response filter / proxy service |
| `X-APP-User`        | Authenticated username          | Response filter / proxy service |
| `X-Gateway-Route`   | Route ID (gateway routes)       | `ProxyResponseHeadersFilter`    |
| `X-Custom-Route`    | Route ID (custom proxy)         | `CustomProxyService`            |
| `X-Proxy-Mode`      | `"custom"` (custom proxy only)  | `CustomProxyUtils`              |

### Correlation ID Flow

```mermaid
sequenceDiagram
    participant Browser
    participant BFF as BFF Gateway
    participant DS as Downstream

    alt Client provides correlation ID
        Browser->>BFF: X-Correlation-Id: abc-123
        BFF->>DS: X-Correlation-Id: abc-123
        DS-->>BFF: X-Correlation-Id: abc-123
        BFF-->>Browser: X-Correlation-Id: abc-123
    else No correlation ID provided
        Browser->>BFF: (no header)
        Note over BFF: Generate UUID: def-456
        BFF->>DS: X-Correlation-Id: def-456
        DS-->>BFF: X-Correlation-Id: def-456
        BFF-->>Browser: X-Correlation-Id: def-456
    end
```

---

## 13. Error Handling

### Error Flow

```mermaid
flowchart TD
    Request["Proxied Request"] --> Try{"Try downstream call"}

    Try -->|"Success"| Success["Return response with proxy headers"]
    Try -->|"4xx / 5xx from downstream"| HttpErr["Preserve status + body<br/>Add proxy headers"]
    Try -->|"Connection refused / timeout"| ConnErr["Throw ProxyRequestException<br/>502 BAD_GATEWAY"]

    ConnErr --> Global["GlobalExceptionHandler"]
    Global --> ErrResponse["JSON error response<br/>+ X-Correlation-Id header"]
```

### Error Response Format

```json
{
  "timestamp": "2026-03-28T10:30:00Z",
  "status": 502,
  "error": "Bad Gateway",
  "message": "Custom proxy target is unavailable for route notifications-custom-route",
  "path": "/custom-proxy/notifications/42",
  "correlationId": "abc-123"
}
```

---

## 14. Configuration Reference

### BFF Gateway (`application.yml`)

```yaml
server:
    port: 8080

app:
    internal-jwt:
        issuer: bff-service              # JWT issuer claim
        audience: internal-api           # JWT audience claim
        ttl-seconds: 300                 # JWT lifetime (5 minutes)
        private-key-location: classpath:keys/bff-jwt-private.pem

    config-service:
        base-url: http://localhost:8085  # Role permission source

    downstream:
        users: http://localhost:8081         # Gateway route target
        orders: http://localhost:8082        # Gateway route target
        notifications: http://localhost:8083 # Custom proxy target
        alerts: http://localhost:8084        # Custom proxy target
```

### Users Service (`application.yml`)

```yaml
server:
    port: 8081

app:
    security:
        jwt:
            jwks-uri: http://localhost:8080/.well-known/jwks.json  # BFF public key
            issuer: bff-service                                     # Must match BFF issuer
            audience: internal-api                                  # Must match BFF audience
    config-service:
        base-url: http://localhost:8085  # Role permission source (independent fetch)
```

### Key Configuration Relationships

```mermaid
flowchart LR
    subgraph BFF["BFF Config"]
        Issuer["issuer: bff-service"]
        Audience["audience: internal-api"]
        PrivKey["private-key-location: keys/bff-jwt-private.pem"]
    end

    subgraph US["Users Service Config"]
        JWKS["jwks-uri: http://localhost:8080/.well-known/jwks.json"]
        ValIssuer["issuer: bff-service"]
        ValAud["audience: internal-api"]
    end

    Issuer -.->|"Must match"| ValIssuer
    Audience -.->|"Must match"| ValAud
    PrivKey -.->|"Public key exposed via"| JWKS
```

---

## File Reference

| File | Purpose |
|------|---------|
| `config/GatewayRoutesConfig.java` | Defines gateway proxy routes with filter chains |
| `config/SecurityConfig.java` | Spring Security setup, form login, in-memory users |
| `config/JwksKeyProvider.java` | Loads RSA keys, publishes JWK Set |
| `filter/InternalJwtRelayFilter.java` | Gateway before-filter: injects JWT into proxied requests |
| `filter/ProxyResponseHeadersFilter.java` | Gateway after-filter: adds traceability headers |
| `controller/CustomProxyController.java` | REST controller for custom proxy routes |
| `controller/RoleController.java` | Role selection and permission endpoints |
| `controller/JwksController.java` | Exposes `/.well-known/jwks.json` |
| `service/InternalJwtService.java` | Signs internal JWTs with RS256 |
| `service/CustomProxyService.java` | Executes custom proxy requests via RestClient |
| `service/RolePermissionService.java` | Fetches role permissions from config-service |
| `security/RoleAwareLoginSuccessHandler.java` | Post-login handler: applies role + permissions |
| `security/SecurityContextHelper.java` | Flattens permissions into SecurityContext authorities |
| `session/SessionKeys.java` | Session attribute constants and accessor |
| `util/CustomProxyUtils.java` | URI building, header management utilities |
| `exception/GlobalExceptionHandler.java` | Centralized error response formatting |
| `exception/ProxyRequestException.java` | Custom exception for proxy failures |
