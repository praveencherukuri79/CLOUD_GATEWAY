package com.example.bff.service;

import com.example.bff.model.RolePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
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
        RolePermissionsResponse response = restClient.get()
                .uri("/api/config/permissions/{role}", role)
                .retrieve()
                .body(RolePermissionsResponse.class);

        if (response == null) {
            return null;
        }

        Map<String, Boolean> flattened = flatten(response.features);
        return new RolePermissions(response.role, flattened);
    }

    private static Map<String, Boolean> flatten(Map<String, Map<String, Boolean>> features) {
        if (features == null || features.isEmpty()) {
            return Map.of();
        }

        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Entry<String, Map<String, Boolean>> featureEntry : features.entrySet()) {
            String feature = featureEntry.getKey();
            Map<String, Boolean> perms = featureEntry.getValue();
            if (feature == null || feature.isBlank() || perms == null) {
                continue;
            }
            for (Entry<String, Boolean> permEntry : perms.entrySet()) {
                String perm = permEntry.getKey();
                if (perm == null || perm.isBlank()) {
                    continue;
                }
                out.put(feature + "_" + perm, Boolean.TRUE.equals(permEntry.getValue()));
            }
        }
        return Map.copyOf(out);
    }

    private record RolePermissionsResponse(String role, Map<String, Map<String, Boolean>> features) {}
}
