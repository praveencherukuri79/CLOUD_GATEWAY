package com.example.bff.controller;

import com.example.bff.service.CustomProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequestMapping("/custom-proxy")
public class CustomProxyController {

    static final String CUSTOM_PROXY_PREFIX = "/custom-proxy";

    final CustomProxyService customProxyService;

    @Value("${app.downstream.notifications}")
    String notificationsServiceUri;

    @Value("${app.downstream.alerts}")
    String alertsServiceUri;

    @RequestMapping({"/notifications", "/notifications/{*path}"})
    public ResponseEntity<byte[]> proxyNotifications(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return customProxyService.forward(
                request,
                body,
                CUSTOM_PROXY_PREFIX,
                notificationsServiceUri,
                "notifications-custom-route");
    }

    @RequestMapping({"/alerts", "/alerts/{*path}"})
    public ResponseEntity<byte[]> proxyAlerts(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        return customProxyService.forward(
                request,
                body,
                CUSTOM_PROXY_PREFIX,
                alertsServiceUri,
                "alerts-custom-route");
    }
}
