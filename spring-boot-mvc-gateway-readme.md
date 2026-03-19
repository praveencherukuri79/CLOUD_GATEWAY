# Spring Boot MVC BFF + Gateway Server MVC + Session Auth + Internal JWT Relay

This README shows a **Spring Boot MVC BFF** that:

- uses **`JSESSIONID` / session auth** for browser requests
- keeps existing **`@RestController`** endpoints
- uses **`spring-cloud-starter-gateway-server-webmvc`** for path forwarding
- creates an **internal JWT** at the BFF when forwarding to downstream microservices
- sends that JWT to the downstream service in `Authorization: Bearer ...`
- downstream microservice validates the JWT as a resource server

---

## Flow

```text
Browser
  -> BFF (Spring Boot MVC, session auth via JSESSIONID)
      -> /api/local/** handled locally by @RestController
      -> /proxy/** routed by Gateway Server MVC
           -> BFF reads authenticated user from SecurityContext
           -> BFF creates short-lived internal JWT
           -> BFF forwards request with Authorization: Bearer <jwt>
  -> Microservice validates JWT and serves API
```

---

## Key points

- You **keep** `spring-boot-starter-web`
- You **add** `spring-cloud-starter-gateway-server-webmvc`
- You do **not** need UI JWT
- You do **not** forward `JSESSIONID` to microservices
- JWT generation should happen in the **gateway before-filter**, not in a servlet filter
- Spring Security runs before MVC gateway handler logic, so `SecurityContextHolder` is available in the gateway before-filter

---

## BFF `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>bff-gateway-mvc</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
        <spring.boot.version>3.3.5</spring.boot.version>
        <spring.cloud.version>2023.0.3</spring.cloud.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring.cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Existing MVC / servlet stack -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Gateway on top of Spring MVC -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
        </dependency>

        <!-- Session auth / Spring Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- JWT creation in BFF -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Tests -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

## BFF application structure

```text
src/main/java/com/example/bff
├── BffGatewayApplication.java
├── config
│   ├── GatewayRoutesConfig.java
│   └── SecurityConfig.java
├── controller
│   └── LocalController.java
├── filter
│   └── InternalJwtRelayFilter.java
└── service
    └── InternalJwtService.java
```

---

## 1) Main application

```java
package com.example.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BffGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffGatewayApplication.class, args);
    }
}
```

---

## 2) Existing local controller

This is your normal MVC controller. It stays as-is.

```java
package com.example.bff.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/local")
public class LocalController {

    @GetMapping("/hello")
    public Map<String, Object> hello(Authentication authentication) {
        return Map.of("message", "local controller response", "user", authentication.getName());
    }
}
```

---

## 3) Spring Security config for BFF

Everything is authenticated with session auth.

This means:

- browser logs in to BFF
- BFF keeps `JSESSIONID`
- `/api/local/**` and `/proxy/**` both require authenticated session

```java
package com.example.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .csrf((csrf) -> csrf.disable())
            .authorizeHttpRequests((auth) ->
                auth.requestMatchers("/login", "/error").permitAll().anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults())
            .build();
    }
}
```

### Why this is enough

You do **not** need a separate JWT security chain on the BFF.

Because:

- UI calls BFF with session auth
- BFF generates JWT only for downstream forwarding
- downstream microservice validates JWT

---

## 4) Internal JWT service in BFF

This creates a **short-lived internal JWT** from the authenticated Spring Security user.

Use:

- `sub` = username
- `roles` = authorities
- `iss` = BFF name
- `aud` = internal-api
- short expiry

```java
package com.example.bff.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class InternalJwtService {

    // Demo secret only. In real code, externalize it and use strong key management.
    private static final String SECRET =
        "replace-this-with-a-long-secure-internal-hmac-secret-key-1234567890";

    private final Key signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public String createToken(Authentication authentication) {
        Instant now = Instant.now();

        List<String> roles = authentication
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        return Jwts.builder()
            .issuer("bff-service")
            .subject(authentication.getName())
            .audience()
            .add("internal-api")
            .and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(300))) // 5 minutes
            .claims(Map.of("roles", roles))
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }
}
```

---

## 5) Gateway before-filter that adds internal JWT

This is the important part.

The request is already authenticated by Spring Security at this point.  
So `SecurityContextHolder` already contains the authenticated session user.

This filter:

- reads `Authentication`
- creates internal JWT
- overwrites/adds `Authorization` header
- returns modified request for forwarding

```java
package com.example.bff.filter;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.before;

import com.example.bff.service.InternalJwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
@RequiredArgsConstructor
public class InternalJwtRelayFilter {

    private final InternalJwtService internalJwtService;

    public java.util.function.Function<ServerRequest, ServerRequest> asBeforeFunction() {
        return (request) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                throw new IllegalStateException("No authenticated user found in SecurityContext");
            }

            String token = internalJwtService.createToken(authentication);

            return ServerRequest.from(request)
                .headers((headers) -> headers.setBearerAuth(token))
                .build();
        };
    }
}
```

---

## 6) Gateway routes config

This defines MVC gateway routes.

### What this does

- `/proxy/users/**` -> `http://localhost:8081/users/**`
- `/proxy/orders/**` -> `http://localhost:8082/orders/**`

`stripPrefix(1)` removes `/proxy` before forwarding.

```java
package com.example.bff.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RouterFunctions.route;

import com.example.bff.filter.InternalJwtRelayFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@RequiredArgsConstructor
public class GatewayRoutesConfig {

    private final InternalJwtRelayFilter internalJwtRelayFilter;

    @Bean
    RouterFunction<ServerResponse> gatewayRoutes() {
        return route()
            .path("/proxy", (builder) ->
                builder
                    // Build internal JWT after session auth is already complete
                    .before(internalJwtRelayFilter.asBeforeFunction())
                    // /proxy/users/123 -> /users/123
                    .before(stripPrefix(1))
                    .route(RequestPredicates.path("/users/**"), http("http://localhost:8081"))
                    .route(RequestPredicates.path("/orders/**"), http("http://localhost:8082"))
            )
            .build();
    }
}
```

---

## Why filter order works here

Execution order is roughly:

```text
Servlet filters
  ->
Spring Security filter chain
  ->
DispatcherServlet
  ->
WebMvc.fn gateway route
  ->
gateway before-filter creates JWT
  ->
request forwarded downstream
```

So the correct place to create internal JWT is the **gateway before-filter**, not a raw servlet filter.

### Do not do this

- do not try to create downstream JWT in a servlet filter before Spring Security
- do not forward `JSESSIONID` to microservice
- do not trust browser-provided `Authorization` header for internal service auth

---

## BFF `application.yml`

```yaml
server:
    port: 8080

logging:
    level:
        org.springframework.security: INFO
        org.springframework.web: INFO
```

---

# Downstream microservice

This microservice validates the internal JWT created by the BFF.

---

## Microservice `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>user-service</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
        <spring.boot.version>3.3.5</spring.boot.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## Microservice main application

```java
package com.example.usersvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
```

---

## Microservice security config

This validates JWT and secures APIs.

```java
package com.example.usersvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
            .csrf((csrf) -> csrf.disable())
            .authorizeHttpRequests((auth) -> auth.anyRequest().authenticated())
            .oauth2ResourceServer((oauth) -> oauth.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

---

## Microservice controller

```java
package com.example.usersvc.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/users/{id}")
    public Map<String, Object> getUser(@PathVariable Long id, Authentication authentication) {
        return Map.of(
            "id",
            id,
            "message",
            "response from downstream user-service",
            "authenticatedPrincipal",
            authentication.getName()
        );
    }
}
```

---

## Microservice `application.yml`

For demo simplicity, this uses the same shared secret as the BFF.

In real production:

- use RSA / EC keys
- BFF signs with private key
- microservice verifies with public key
- prefer `issuer-uri` or JWK set when available

```yaml
server:
    port: 8081

spring:
    security:
        oauth2:
            resourceserver:
                jwt:
                    secret-key: "replace-this-with-a-long-secure-internal-hmac-secret-key-1234567890"
```

> Note: Depending on your Spring Boot version, `secret-key` convenience support may vary.  
> If needed, configure a `JwtDecoder` bean manually.

---

## Manual `JwtDecoder` bean if needed

Use this if property-based secret config is not enough in your version.

```java
package com.example.usersvc.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {

    private static final String SECRET =
        "replace-this-with-a-long-secure-internal-hmac-secret-key-1234567890";

    @Bean
    JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
```

---

# Test it

## 1) Start downstream user-service on `8081`

## 2) Start BFF on `8080`

## 3) Login to BFF

Use Spring Security default login page or your existing session login flow.

## 4) Call local endpoint

```bash
curl -b cookies.txt -c cookies.txt http://localhost:8080/api/local/hello
```

## 5) Call forwarded endpoint through BFF

```bash
curl -b cookies.txt -c cookies.txt http://localhost:8080/proxy/users/123
```

Expected:

- browser/client only uses session cookie with BFF
- BFF generates internal JWT
- user-service receives bearer token and validates it
- user-service returns authenticated response

---

# Notes and drawbacks

## 1) Mixed app responsibilities

Your BFF now does:

- local MVC endpoints
- session auth
- gateway routing
- token issuance

That is acceptable, but do not pretend it stays tiny forever.

## 2) Shared secret demo is not ideal

For production:

- use asymmetric signing
- short-lived tokens
- define `iss`, `aud`, `exp`
- rotate keys properly

## 3) Keep `/proxy/**` separate

Do not mix gateway paths with local controller paths.

Good:

- `/api/local/**`
- `/proxy/**`

Bad:

- local controllers and proxy routes under same ambiguous prefix

## 4) Do not let browser send internal token

The internal JWT is BFF-to-service only.

## 5) Avoid heavy servlet filters on `/proxy/**`

Do not consume request body or mutate headers in weird ways before forwarding.

---

# Summary

For your exact use case:

- UI authenticates to BFF with session / `JSESSIONID`
- BFF handles local APIs normally
- BFF routes `/proxy/**` using `spring-cloud-starter-gateway-server-webmvc`
- BFF creates a short-lived internal JWT for the downstream call
- downstream microservice validates JWT and serves API

That is the clean model.
