package com.cognition.est.app.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller as asserted by the access token. Every service call is scoped by {@link #orgId()}. */
public record Principal(String memberId, String orgId, String role, String email) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public static Principal of(Jwt jwt) {
        return new Principal(
                jwt.getSubject(),
                jwt.getClaimAsString("org"),
                jwt.getClaimAsString("role"),
                jwt.getClaimAsString("email"));
    }

    public static Principal current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt))
            throw new IllegalStateException("No authenticated principal");
        return of(jwt);
    }
}
