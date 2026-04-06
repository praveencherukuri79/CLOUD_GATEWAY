package com.example.bff.security;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("perm")
public class PermissionEvaluatorBean {

    private final PermissionService permissionService;

    public PermissionEvaluatorBean(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean check(Authentication authentication, String permissionKey) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (permissionKey == null || permissionKey.isBlank()) {
            return false;
        }

        return permissionService.hasAllPermissions(authentication, List.of(permissionKey));
    }

    public boolean checkAll(Authentication authentication, List<String> permissionKeys) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return permissionService.hasAllPermissions(authentication, permissionKeys);
    }

    public boolean checkAny(Authentication authentication, List<String> permissionKeys) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return permissionService.hasAnyPermissions(authentication, permissionKeys);
    }
}
