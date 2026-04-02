package com.example.bff.security;

import com.example.bff.model.RolePermissions;
import com.example.bff.service.RolePermissionService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final RolePermissionService rolePermissionService;

    public PermissionService(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    public boolean hasAccess(Authentication authentication, String feature, String action) {
        if (feature == null || feature.isBlank() || action == null || action.isBlank()) {
            return false;
        }

        AuthContext auth = AuthUtils.authContext(authentication);
        if (auth == null) {
            return false;
        }

        String roleId = auth.getSelectedRoleId();
        if (roleId == null || roleId.isBlank()) {
            return false;
        }

        RolePermissions permissions = rolePermissionService.fetchPermissions(roleId);
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
