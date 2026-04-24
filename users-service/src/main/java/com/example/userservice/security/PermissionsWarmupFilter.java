package com.example.userservice.security;

import com.example.userservice.service.ConfigServiceClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@RequiredArgsConstructor
public class PermissionsWarmupFilter extends OncePerRequestFilter {

    private final ConfigServiceClient configServiceClient;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String roleId = AuthUtils.selectedRoleId(authentication);

        if (authentication != null && authentication.isAuthenticated() && roleId != null && !roleId.isBlank()) {
            try {
                configServiceClient.getPermissions(roleId);
            } catch (RuntimeException ex) {
                // Non-fatal warmup: permission checks will still enforce access.
                log.debug("Permission cache warmup failed for roleId={} path={}", roleId, request.getRequestURI(), ex);
            }
        }

        filterChain.doFilter(request, response);
    }
}
