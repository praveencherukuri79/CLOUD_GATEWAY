package com.example.userservice.security;

import com.example.userservice.model.RolePermissions;
import com.example.userservice.service.ConfigServiceClient;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final ConfigServiceClient configServiceClient;

    public PermissionService(ConfigServiceClient configServiceClient) {
        this.configServiceClient = configServiceClient;
    }

    public boolean hasAllPermissions(String roleId, List<String> permissionKeys) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        if (permissionKeys == null || permissionKeys.isEmpty()) {
            return false;
        }

        RolePermissions permissions = configServiceClient.getPermissions(roleId);
        if (permissions == null || permissions.permissions() == null) {
            return false;
        }

        for (String key : permissionKeys) {
            if (key == null || key.isBlank()) {
                return false;
            }
            if (!Boolean.TRUE.equals(permissions.permissions().get(key))) {
                return false;
            }
        }

        return true;
    }

    public boolean hasAnyPermissions(String roleId, List<String> permissionKeys) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        if (permissionKeys == null || permissionKeys.isEmpty()) {
            return false;
        }

        RolePermissions permissions = configServiceClient.getPermissions(roleId);
        if (permissions == null || permissions.permissions() == null) {
            return false;
        }

        for (String key : permissionKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            if (Boolean.TRUE.equals(permissions.permissions().get(key))) {
                return true;
            }
        }

        return false;
    }
}
