package com.example.userservice.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

@Component("perm")
public class PermissionEvaluatorBean {

    private final PermissionService permissionService;

    public PermissionEvaluatorBean(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean check(Authentication authentication, String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return false;
        }
        return checkAll(authentication, List.of(permissionKey));
    }

    public boolean checkAll(Authentication authentication, List<String> permissionKeys) {
        AuthContext auth = AuthUtils.authContext(authentication);
        if (auth == null) {
            return false;
        }

        String roleId = auth.getSelectedRoleId();
        if (roleId == null || roleId.isBlank()) {
            throw new InsufficientAuthenticationException("Missing activeRole claim");
        }

        try {
            return permissionService.hasAllPermissions(roleId, permissionKeys);
        } catch (RuntimeException ex) {
            throw new InsufficientAuthenticationException("Unable to evaluate permissions", ex);
        }
    }

    public boolean checkAny(Authentication authentication, List<String> permissionKeys) {
        AuthContext auth = AuthUtils.authContext(authentication);
        if (auth == null) {
            return false;
        }

        String roleId = auth.getSelectedRoleId();
        if (roleId == null || roleId.isBlank()) {
            throw new InsufficientAuthenticationException("Missing activeRole claim");
        }

        try {
            return permissionService.hasAnyPermissions(roleId, permissionKeys);
        } catch (RuntimeException ex) {
            throw new InsufficientAuthenticationException("Unable to evaluate permissions", ex);
        }
    }
}
