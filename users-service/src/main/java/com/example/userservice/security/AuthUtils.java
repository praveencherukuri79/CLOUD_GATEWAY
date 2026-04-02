package com.example.userservice.security;

import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

public final class AuthUtils {

    private AuthUtils() {}

    @Nullable
    public static AuthContext authContext(@Nullable Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        return principal instanceof AuthContext ctx ? ctx : null;
    }

    @Nullable
    public static String selectedRoleId(@Nullable Authentication authentication) {
        AuthContext ctx = authContext(authentication);
        return ctx != null ? ctx.getSelectedRoleId() : null;
    }

    public static Map<String, Object> claims(@Nullable Authentication authentication) {
        AuthContext ctx = authContext(authentication);
        if (ctx == null || ctx.getClaims() == null) {
            return Map.of();
        }
        return ctx.getClaims();
    }
}
