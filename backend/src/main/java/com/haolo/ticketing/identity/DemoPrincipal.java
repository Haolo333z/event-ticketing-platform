package com.haolo.ticketing.identity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record DemoPrincipal(UUID accountId, String role, List<String> roles) {
    public DemoPrincipal(UUID accountId, String role) {
        this(accountId, role, role != null ? List.of(role) : Collections.emptyList());
    }

    public List<String> roles() {
        if (roles != null && !roles.isEmpty()) {
            return roles;
        }
        return role != null ? List.of(role) : Collections.emptyList();
    }

    public boolean hasRole(String targetRole) {
        if (targetRole == null) return false;
        if (roles != null && roles.contains(targetRole)) return true;
        return targetRole.equals(role);
    }
}
