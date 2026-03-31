package com.example.bff.service;

import com.example.bff.model.RolePermissions;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RolePermissionService {

    RestClient restClient;

    public RolePermissionService(@Value("${app.config-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Cacheable(cacheNames = "rolePermissions", key = "#role", unless = "#result == null")
    public RolePermissions fetchPermissions(String role) {
        log.debug("Fetching permissions from config-service for role={}", role);
        return restClient.get()
                .uri("/api/config/permissions/{role}", role)
                .retrieve()
                .body(RolePermissions.class);
    }
}
