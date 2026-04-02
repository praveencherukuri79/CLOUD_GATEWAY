package com.example.bff.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<Map<String, Object>> handleAuthenticationException(
                        AuthenticationException ex, HttpServletRequest request) {
                String correlationId = resolveCorrelationId(request);
                return buildResponse(
                                HttpStatus.UNAUTHORIZED, ex.getMessage(), correlationId, request.getRequestURI());
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
                        AccessDeniedException ex, HttpServletRequest request) {
                String correlationId = resolveCorrelationId(request);
                return buildResponse(
                                HttpStatus.FORBIDDEN, ex.getMessage(), correlationId, request.getRequestURI());
        }

    @ExceptionHandler(ProxyRequestException.class)
    public ResponseEntity<Map<String, Object>> handleProxyRequestException(
            ProxyRequestException ex, HttpServletRequest request) {
        log.warn(
                "Proxy request failed path={} correlationId={} message={}",
                request.getRequestURI(),
                ex.getCorrelationId(),
                ex.getMessage());
        return buildResponse(
                ex.getStatus(), ex.getMessage(), ex.getCorrelationId(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        HttpStatus status =
                request.getRequestURI().startsWith("/proxy/")
                                || request.getRequestURI().startsWith("/custom-proxy/")
                        ? HttpStatus.BAD_GATEWAY
                        : HttpStatus.INTERNAL_SERVER_ERROR;
                String correlationId = resolveCorrelationId(request);

        log.error(
                "Unhandled request failure path={} correlationId={}",
                request.getRequestURI(),
                correlationId,
                ex);
        return buildResponse(status, ex.getMessage(), correlationId, request.getRequestURI());
    }

        private String resolveCorrelationId(HttpServletRequest request) {
                String correlationId = request.getHeader("X-Correlation-Id");
                if (correlationId == null || correlationId.isBlank()) {
                        correlationId = UUID.randomUUID().toString();
                }
                return correlationId;
        }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, String correlationId, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        body.put("correlationId", correlationId);

        return ResponseEntity.status(status)
                .header("X-Correlation-Id", correlationId)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(body);
    }
}
