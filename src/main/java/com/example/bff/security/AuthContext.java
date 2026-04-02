package com.example.bff.security;

import java.security.Principal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthContext implements Principal {

    private String userId;

    private String username;

    private String tenantId;

    private String selectedRoleId;

    private String selectedRoleType;

    private String email;

    private Map<String, Object> claims;

    @Override
    public String getName() {
        return username != null ? username : "";
    }
}
