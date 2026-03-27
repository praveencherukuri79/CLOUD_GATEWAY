package com.example.bff.controller;

import com.example.bff.model.RolePermissions;
import com.example.bff.service.RolePermissionService;
import com.example.bff.session.SessionKeys;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleController {

    RolePermissionService rolePermissionService;

    @GetMapping("/available")
    public Map<String, Object> availableRoles(Authentication authentication, HttpSession session) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        String activeRole = (String) session.getAttribute(SessionKeys.ACTIVE_ROLE);

        return Map.of(
                "roles", roles,
                "activeRole", activeRole != null ? activeRole : "",
                "selectionRequired", Boolean.TRUE.equals(session.getAttribute(SessionKeys.ROLE_SELECTION_REQUIRED)));
    }

    @PostMapping("/select")
    public Map<String, Object> selectRole(@RequestBody Map<String, String> body,
                                          Authentication authentication, HttpSession session) {
        String selectedRole = body.get("role");
        if (selectedRole == null || selectedRole.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }

        boolean hasRole = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(selectedRole::equals);

        if (!hasRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have role: " + selectedRole);
        }

        RolePermissions permissions = rolePermissionService.fetchPermissions(selectedRole);
        session.setAttribute(SessionKeys.ACTIVE_ROLE, selectedRole);
        session.setAttribute(SessionKeys.ROLE_PERMISSIONS, permissions);
        session.removeAttribute(SessionKeys.ROLE_SELECTION_REQUIRED);

        return Map.of(
                "activeRole", selectedRole,
                "features", permissions.getFeatures());
    }

    @GetMapping("/permissions")
    public Map<String, Object> currentPermissions(HttpSession session) {
        String activeRole = (String) session.getAttribute(SessionKeys.ACTIVE_ROLE);
        if (activeRole == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No role selected");
        }

        RolePermissions permissions = SessionKeys.getPermissions(session);
        return Map.of(
                "activeRole", activeRole,
                "features", permissions.getFeatures());
    }
}
