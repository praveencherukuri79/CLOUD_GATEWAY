package com.example.bff.filter;

import com.example.bff.exception.ProxyRequestException;
import com.example.bff.service.InternalJwtService;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalJwtRelayFilter {

    public static final String APP_USER_HEADER = "X-APP-User";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    InternalJwtService internalJwtService;

    public Function<ServerRequest, ServerRequest> asBeforeFunction() {
        return request -> {
            String correlationId = resolveCorrelationId(request);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

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

            String token = internalJwtService.createToken(authentication);

            return ServerRequest.from(request)
                    .headers(
                            headers -> {
                                headers.remove(HttpHeaders.COOKIE);
                                headers.setBearerAuth(token);
                                headers.set(APP_USER_HEADER, authentication.getName());
                                headers.set(CORRELATION_ID_HEADER, correlationId);
                            })
                    .build();
        };
    }

    private String resolveCorrelationId(ServerRequest request) {
        return Optional.ofNullable(request.headers().firstHeader(CORRELATION_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }
}
