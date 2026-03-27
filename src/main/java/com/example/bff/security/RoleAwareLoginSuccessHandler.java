package com.example.bff.security;

import com.example.bff.model.RolePermissions;
import com.example.bff.service.RolePermissionService;
import com.example.bff.session.SessionKeys;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleAwareLoginSuccessHandler implements AuthenticationSuccessHandler {

    RolePermissionService rolePermissionService;
    SavedRequestAwareAuthenticationSuccessHandler defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        HttpSession session = request.getSession();
        log.info("User {} logged in with roles={}", authentication.getName(), roles);

        if (roles.size() == 1) {
            applyRole(session, roles.get(0));
            defaultHandler.onAuthenticationSuccess(request, response, authentication);
        } else {
            session.setAttribute(SessionKeys.ROLE_SELECTION_REQUIRED, true);
            response.sendRedirect("/role-selection");
        }
    }

    private void applyRole(HttpSession session, String role) {
        RolePermissions permissions = rolePermissionService.fetchPermissions(role);
        session.setAttribute(SessionKeys.ACTIVE_ROLE, role);
        session.setAttribute(SessionKeys.ROLE_PERMISSIONS, permissions);
        session.removeAttribute(SessionKeys.ROLE_SELECTION_REQUIRED);
        log.info("Applied role={} with {} features", role, permissions.getFeatures().size());
    }
}
