package com.example.bff.session;

import com.example.bff.model.RolePermissions;
import com.example.bff.service.RolePermissionService;
import jakarta.servlet.http.HttpSession;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleSessionService {

    RolePermissionService rolePermissionService;

    public RolePermissions applyRole(Authentication authentication, HttpSession session, String role) {
        RolePermissions permissions = rolePermissionService.fetchPermissions(role);

        session.setAttribute(SessionKeys.ACTIVE_ROLE, role);
        session.setAttribute(SessionKeys.ROLE_PERMISSIONS, permissions);
        session.removeAttribute(SessionKeys.ROLE_SELECTION_REQUIRED);

        int featureCount = permissions != null && permissions.getFeatures() != null
                ? permissions.getFeatures().size()
                : 0;
        log.info("Applied role={} with {} features", role, featureCount);

        return permissions;
    }

    public void markRoleSelectionRequired(HttpSession session) {
        session.setAttribute(SessionKeys.ROLE_SELECTION_REQUIRED, true);
    }
}
