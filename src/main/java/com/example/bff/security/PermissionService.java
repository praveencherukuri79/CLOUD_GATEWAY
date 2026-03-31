package com.example.bff.security;

import com.example.bff.model.RolePermissions;
import com.example.bff.service.RolePermissionService;
import com.example.bff.session.SessionKeys;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final RolePermissionService rolePermissionService;

    public PermissionService(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    public boolean hasAccess(HttpSession session, String feature, String action) {
        if (session == null) {
            return false;
        }
        if (feature == null || feature.isBlank() || action == null || action.isBlank()) {
            return false;
        }

        RolePermissions permissions = SessionKeys.getPermissions(session);
        String activeRole = (String) session.getAttribute(SessionKeys.ACTIVE_ROLE);

        if ((permissions == null || permissions.getFeatures() == null)
                && activeRole != null && !activeRole.isBlank()) {
            RolePermissions fetched = rolePermissionService.fetchPermissions(activeRole);
            session.setAttribute(SessionKeys.ROLE_PERMISSIONS, fetched);
            permissions = fetched;
        }

        if (permissions == null || permissions.getFeatures() == null) {
            return false;
        }

        Map<String, Boolean> featurePerms = permissions.getFeatures().get(feature);
        if (featurePerms == null) {
            return false;
        }

        return Boolean.TRUE.equals(featurePerms.get(action));
    }
}
