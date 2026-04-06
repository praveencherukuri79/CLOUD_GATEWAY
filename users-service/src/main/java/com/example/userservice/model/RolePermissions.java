package com.example.userservice.model;

import java.util.Map;

public record RolePermissions(String role, Map<String, Boolean> permissions) {
}
