package com.example.bff.filter;

import com.example.bff.exception.ProxyRequestException;
import com.example.bff.security.AuthContext;
import com.example.bff.security.AuthUtils;
import com.example.bff.service.InternalJwtService;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.ServerRequest;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalJwtRelayFilter {

    public static final String APP_USER_HEADER = "X-APP-User";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String JWT_ISSUER_HEADER = "X-Internal-JWT-Issuer";
    public static final String ACTIVE_ROLE_ID_HEADER = "X-Active-Role-Id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    InternalJwtService internalJwtService;

    @NonFinal
    @Value("${app.internal-jwt.issuer}")
    String internalJwtIssuer;

    public Function<ServerRequest, ServerRequest> asBeforeFunction() {
        return request -> {
            String correlationId = resolveCorrelationId(request);

            Authentication authentication = AuthUtils.currentAuthentication();

            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                log.warn(
                        "No authenticated user found for proxied request path={} correlationId={}",
                        request.path(),
                        correlationId);
                throw new ProxyRequestException(
                        HttpStatus.UNAUTHORIZED,
                        "No authenticated user found in SecurityContext",
                        correlationId);
            }

            String activeRole = AuthUtils.selectedRoleId(authentication);
            AuthContext authContext = AuthUtils.authContext(authentication);

            String token = internalJwtService.createToken(authentication, resolveClientIp(request), activeRole);

            return ServerRequest.from(request)
                    .headers(
                            headers -> {
                                headers.remove(HttpHeaders.COOKIE);
                                headers.setBearerAuth(token);
                                headers.set(APP_USER_HEADER, authentication.getName());
                                headers.set(CORRELATION_ID_HEADER, correlationId);
                                if (StringUtils.hasText(internalJwtIssuer)) {
                                    headers.set(JWT_ISSUER_HEADER, internalJwtIssuer);
                                }
                                if (StringUtils.hasText(activeRole)) {
                                    headers.set(ACTIVE_ROLE_ID_HEADER, activeRole);
                                } else {
                                    headers.remove(ACTIVE_ROLE_ID_HEADER);
                                }
                                if (authContext != null && StringUtils.hasText(authContext.getTenantId())) {
                                    headers.set(TENANT_ID_HEADER, authContext.getTenantId());
                                } else {
                                    headers.remove(TENANT_ID_HEADER);
                                }
                            })
                    .build();
        };
    }

    private String resolveCorrelationId(ServerRequest request) {
        return Optional.ofNullable(request.headers().firstHeader(CORRELATION_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    private String resolveClientIp(ServerRequest request) {
        String forwardedFor = request.headers().firstHeader(X_FORWARDED_FOR_HEADER);

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.servletRequest().getRemoteAddr();
    }
}
