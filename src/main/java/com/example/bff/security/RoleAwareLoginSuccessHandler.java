package com.example.bff.security;

import com.example.bff.session.RoleSessionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    RoleSessionService roleSessionService;
    SavedRequestAwareAuthenticationSuccessHandler defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        log.info("User {} logged in with roles={}", authentication.getName(), roles);

        roleSessionService.applyAuthContext(authentication, "");

        if (roles.size() == 1) {
            roleSessionService.applyRole(authentication, roles.get(0));
            Authentication updated = AuthUtils.currentAuthentication();
            defaultHandler.onAuthenticationSuccess(request, response, updated != null ? updated : authentication);
        } else {
            response.sendRedirect("/role-selection");
        }
    }
}
