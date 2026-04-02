package com.example.userservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

@Component("perm")
public class PermissionEvaluatorBean {

    private final PermissionService permissionService;

    public PermissionEvaluatorBean(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean check(Authentication authentication, String feature, String action) {
        AuthContext auth = AuthUtils.authContext(authentication);
        if (auth == null) {
            return false;
        }

        String roleId = auth.getSelectedRoleId();
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
