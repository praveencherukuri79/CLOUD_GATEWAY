package com.example.userservice.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController {

    @GetMapping("/public/ping")
    public Map<String, String> ping() {
        return Map.of("message", "users service is running");
    }

    @GetMapping("/users/{userId}")
    public Map<String, Object> getUser(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "X-APP-User", required = false) String appUser,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", userId);
        response.put("message", "users service accepted the JWT");
        response.put("jwtSubject", jwt.getSubject());
        response.put("jwtIssuer", jwt.getClaimAsString("iss"));
        response.put("jwtAudience", jwt.getAudience());
        response.put("roles", jwt.getClaimAsStringList("roles") != null
                ? jwt.getClaimAsStringList("roles") : List.of());
        response.put("xAppUser", appUser != null ? appUser : "n/a");
        response.put("correlationId", correlationId != null ? correlationId : "n/a");
        return response;

        // jwt.getSubject()
        // jwt.getClaimAsString("email")
        // jwt.getClaimAsString("username")
        // jwt.getClaimAsStringList("roles")
        // jwt.getClaimAsString("ipAddress")
    }
}
