package com.example.bff.session;

import com.example.bff.model.RolePermissions;
import jakarta.servlet.http.HttpSession;

public final class SessionKeys {

    private SessionKeys() {}

    public static final String ACTIVE_ROLE = "ACTIVE_ROLE";
    public static final String ROLE_PERMISSIONS = "ROLE_PERMISSIONS";
    public static final String ROLE_SELECTION_REQUIRED = "ROLE_SELECTION_REQUIRED";

    public static RolePermissions getPermissions(HttpSession session) {
        return (RolePermissions) session.getAttribute(ROLE_PERMISSIONS);
    }
}
