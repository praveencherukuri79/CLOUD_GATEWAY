package com.example.userservice.security;

import com.example.userservice.model.RolePermissions;
import com.example.userservice.service.ConfigServiceClient;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final ConfigServiceClient configServiceClient;

    public PermissionService(ConfigServiceClient configServiceClient) {
        this.configServiceClient = configServiceClient;
    }

    public boolean hasAccess(String roleId, String feature, String action) {
        if (roleId == null || roleId.isBlank()) {
            return false;
        }
        if (feature == null || feature.isBlank() || action == null || action.isBlank()) {
            return false;
        }

        RolePermissions permissions = configServiceClient.getPermissions(roleId);
        if (permissions == null || permissions.features() == null) {
            return false;
        }

        Map<String, Boolean> featurePerms = permissions.features().get(feature);
        if (featurePerms == null) {
            return false;
        }

        return Boolean.TRUE.equals(featurePerms.get(action));
    }
}
