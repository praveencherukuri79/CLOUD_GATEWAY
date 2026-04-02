package com.example.bff.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class AuthUtils {

    private AuthUtils() {}

    @Nullable
    public static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Nullable
    public static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    @Nullable
    public static HttpSession currentSession() {
        return sessionFromRequest(currentRequest());
    }

    @Nullable
    public static HttpSession sessionFromRequest(@Nullable HttpServletRequest request) {
        return request != null ? request.getSession(false) : null;
    }

    @Nullable
    public static HttpSession sessionFromAuthentication(@Nullable Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object details = authentication.getDetails();
        if (details instanceof HttpSession session) {
            return session;
        }

        if (details instanceof WebAuthenticationDetails webDetails) {
            String sessionId = webDetails.getSessionId();
            HttpSession current = currentSession();
            if (current != null && sessionId != null && sessionId.equals(current.getId())) {
                return current;
            }
        }

        return null;
    }
}
