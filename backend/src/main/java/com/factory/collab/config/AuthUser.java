package com.factory.collab.config;

import com.factory.collab.domain.Role;
import com.factory.collab.domain.User;
import org.springframework.security.core.AuthenticatedPrincipal;

import java.security.Principal;

/** Authenticated principal for both HTTP requests and STOMP sessions. {@link #getName()} is the username. */
public record AuthUser(Long id, String username, String displayName, Long departmentId,
                       String departmentCode, String departmentName, Role role)
        implements Principal, AuthenticatedPrincipal {

    public static AuthUser of(User u) {
        return new AuthUser(u.getId(), u.getUsername(), u.getDisplayName(), u.getDepartment().getId(),
                u.getDepartment().getCode(), u.getDepartment().getName(), u.getRole());
    }

    @Override
    public String getName() {
        return username;
    }
}
