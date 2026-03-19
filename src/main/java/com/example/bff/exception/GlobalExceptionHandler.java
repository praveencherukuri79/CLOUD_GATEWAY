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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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
        String correlationId = request.getHeader("X-Correlation-Id");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        log.error(
                "Unhandled request failure path={} correlationId={}",
                request.getRequestURI(),
                correlationId,
                ex);
        return buildResponse(status, ex.getMessage(), correlationId, request.getRequestURI());
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
