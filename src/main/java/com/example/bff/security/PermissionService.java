package com.example.bff.security;

import com.example.bff.model.RolePermissions;
import com.example.bff.service.RolePermissionService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final RolePermissionService rolePermissionService;

    public PermissionService(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    public boolean hasAllPermissions(Authentication authentication, List<String> permissionKeys) {
        if (permissionKeys == null || permissionKeys.isEmpty()) {
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
        if (permissions == null || permissions.getPermissions() == null) {
            return false;
        }

        for (String key : permissionKeys) {
            if (key == null || key.isBlank()) {
                return false;
            }
            if (!Boolean.TRUE.equals(permissions.getPermissions().get(key))) {
                return false;
            }
        }

        return true;
    }

    public boolean hasAnyPermissions(Authentication authentication, List<String> permissionKeys) {
        if (permissionKeys == null || permissionKeys.isEmpty()) {
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
        if (permissions == null || permissions.getPermissions() == null) {
            return false;
        }

        for (String key : permissionKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(permissions.getPermissions().get(key))) {
                return true;
            }
        }

        return false;
    }
}
