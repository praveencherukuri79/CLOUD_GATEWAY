package com.example.bff.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("perm")
public class PermissionEvaluatorBean {

    private final PermissionService permissionService;

    public PermissionEvaluatorBean(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public boolean check(Authentication authentication, String feature, String action) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        HttpSession session = AuthUtils.currentSession();
        return permissionService.hasAccess(session, feature, action);
    }
}
