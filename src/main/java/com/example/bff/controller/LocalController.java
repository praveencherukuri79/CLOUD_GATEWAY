package com.example.bff.controller;

import com.example.bff.filter.InternalJwtRelayFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/local")
public class LocalController {

    @GetMapping("/hello")
    public Map<String, Object> hello(Authentication authentication, HttpServletRequest request) {
        String correlationId =
                Optional.ofNullable(request.getHeader(InternalJwtRelayFilter.CORRELATION_ID_HEADER))
                        .filter(value -> !value.isBlank())
                        .orElseGet(() -> UUID.randomUUID().toString());
        return Map.of(
                "message",
                "local controller response",
                "user",
                authentication.getName(),
                "authorities",
                authentication.getAuthorities(),
                "correlationId",
                correlationId,
                "requestHeaders",
                Map.of(
                        InternalJwtRelayFilter.CORRELATION_ID_HEADER,
                        correlationId,
                        HttpHeaders.COOKIE,
                        Optional.ofNullable(request.getHeader(HttpHeaders.COOKIE))
                                .orElse("<none>")));
    }
}
