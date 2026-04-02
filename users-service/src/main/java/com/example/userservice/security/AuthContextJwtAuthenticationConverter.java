package com.example.userservice.security;

import java.util.Collections;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

public class AuthContextJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String userId = jwt.getSubject();

        String username = jwt.getClaimAsString("username");
        if (username == null || username.isBlank()) {
            username = jwt.getClaimAsString("preferred_username");
        }
        if (username == null || username.isBlank()) {
            username = userId;
        }

        AuthContext authContext = AuthContext.builder()
                .userId(userId != null ? userId : "")
                .username(username != null ? username : "")
                .tenantId(safeString(jwt.getClaimAsString("tenantId")))
                .selectedRoleId(safeString(jwt.getClaimAsString("activeRole")))
                .selectedRoleType(safeString(jwt.getClaimAsString("activeRoleType")))
                .email(safeString(jwt.getClaimAsString("email")))
                .claims(jwt.getClaims() != null ? Map.copyOf(jwt.getClaims()) : Collections.emptyMap())
                .build();

        return new UsernamePasswordAuthenticationToken(authContext, null, Collections.emptyList());
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }
}
