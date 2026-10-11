package com.cognition.est.domain.model;

public record Member(
        String id,
        String orgId,
        String email,
        String name,
        String title,
        String role,
        String status,
        int hue,
        String timeZone) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
