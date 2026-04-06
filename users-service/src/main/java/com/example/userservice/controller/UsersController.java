package com.example.userservice.controller;

import com.example.userservice.security.AuthContext;
import com.example.userservice.security.AuthUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

    @PreAuthorize("@perm.check(authentication, 'users_view')")
    @GetMapping("/users/{userId}")
    public Map<String, Object> getUser(
            @PathVariable String userId,
            Authentication authentication,
            @RequestHeader(name = "X-APP-User", required = false) String appUser,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId) {

        AuthContext auth = AuthUtils.authContext(authentication);
        Map<String, Object> claims = AuthUtils.claims(authentication);

        String activeRole = auth != null ? auth.getSelectedRoleId() : null;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", userId);
        response.put("message", "users service accepted the JWT");
        response.put("jwtSubject", auth != null ? auth.getUserId() : "n/a");
        response.put("jwtIssuer", claims.getOrDefault("iss", "n/a"));
        response.put("jwtAudience", claims.getOrDefault("aud", List.of()));
        response.put("activeRole", activeRole != null ? activeRole : "n/a");
        Object roles = claims.get("roles");
        response.put("roles", roles != null ? roles : List.of());
        response.put("xAppUser", appUser != null ? appUser : "n/a");
        response.put("correlationId", correlationId != null ? correlationId : "n/a");
        return response;
    }
}
