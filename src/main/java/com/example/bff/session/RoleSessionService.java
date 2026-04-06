package com.example.bff.session;

import com.example.bff.model.RolePermissions;
import com.example.bff.security.AuthContext;
import com.example.bff.security.AuthUtils;
import com.example.bff.service.RolePermissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleSessionService {

    RolePermissionService rolePermissionService;

    public void applyAuthContext(Authentication authentication, String selectedRoleId) {
        if (authentication == null) {
            return;
        }

        AuthContext existing = AuthUtils.authContext(authentication);

        String username = authentication.getName();
        String roleId = selectedRoleId != null ? selectedRoleId : "";

        Map<String, Object> claims = new LinkedHashMap<>();
        if (existing != null && existing.getClaims() != null) {
            claims.putAll(existing.getClaims());
        }
        claims.put("selectedRoleId", roleId);
        claims.put(
                "roles",
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()));

        AuthContext authContext = AuthContext.builder()
                .userId(existing != null ? safe(existing.getUserId()) : safe(username))
                .username(existing != null ? safe(existing.getUsername()) : safe(username))
                .tenantId(existing != null ? safe(existing.getTenantId()) : "")
                .selectedRoleId(roleId)
                .selectedRoleType(existing != null ? safe(existing.getSelectedRoleType()) : "")
                .email(existing != null ? existing.getEmail() : null)
                .claims(claims)
                .build();

        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                authContext,
                null,
                authentication.getAuthorities());
        newAuth.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }

    public RolePermissions applyRole(Authentication authentication, String role) {
        RolePermissions permissions = rolePermissionService.fetchPermissions(role);

        applyAuthContext(authentication, role);

        int permissionCount = permissions != null && permissions.getPermissions() != null
            ? permissions.getPermissions().size()
                : 0;
        log.info("Applied role={} with {} permissions", role, permissionCount);

        return permissions;
    }

    public RolePermissions getSelectedRolePermissions(Authentication authentication) {
        String roleId = selectedRoleId(authentication);
        if (roleId == null || roleId.isBlank()) {
            return null;
        }
        return rolePermissionService.fetchPermissions(roleId);
    }

    public String selectedRoleId(Authentication authentication) {
        return AuthUtils.selectedRoleId(authentication);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
