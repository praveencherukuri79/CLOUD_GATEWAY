package com.example.userservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("perm")
public class PermissionEvaluatorBean {

    private final PermissionService permissionService;

    public PermissionEvaluatorBean(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean check(Authentication authentication, String feature, String action) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }

        String roleId = jwt.getClaimAsString("activeRole");
        if (roleId == null || roleId.isBlank()) {
            throw new InsufficientAuthenticationException("Missing activeRole claim");
        }

        try {
            return permissionService.hasAccess(roleId, feature, action);
        } catch (RuntimeException ex) {
            throw new InsufficientAuthenticationException("Unable to evaluate permissions", ex);
        }
    }
}
