package com.example.userservice.service;

import com.example.userservice.model.RolePermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ConfigServiceClient {

    private final RestClient restClient;

    public ConfigServiceClient(@Value("${app.config-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public RolePermissions getPermissions(String role) {
        return restClient.get()
                .uri("/api/config/permissions/{role}", role)
                .retrieve()
                .body(RolePermissions.class);
    }
}
