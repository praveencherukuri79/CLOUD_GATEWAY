package com.example.bff.security;

import com.example.bff.model.RolePermissions;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextHelper {

    private SecurityContextHelper() {}

    /**
     * Flattens enabled feature permissions into flat authority keys
     * (e.g. "users.view", "orders.edit") and replaces the current
     * Authentication with one that includes both the original role
     * authorities and the new permission authorities.
     */
    public static void applyPermissionAuthorities(Authentication currentAuth, RolePermissions permissions) {
        List<GrantedAuthority> updatedAuthorities = new ArrayList<>(currentAuth.getAuthorities());

        if (permissions != null && permissions.getFeatures() != null) {
            permissions.getFeatures().forEach((feature, featurePerms) ->
                    featurePerms.forEach((perm, enabled) -> {
                        if (Boolean.TRUE.equals(enabled)) {
                            updatedAuthorities.add(new SimpleGrantedAuthority(feature + "." + perm));
                        }
                    }));
        }

        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                currentAuth.getPrincipal(), currentAuth.getCredentials(), updatedAuthorities);
        newAuth.setDetails(currentAuth.getDetails());
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }
}
