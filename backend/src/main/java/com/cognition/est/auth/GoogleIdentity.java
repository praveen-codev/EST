package com.cognition.est.auth;

/** The verified claims we keep from a Google ID token. */
public record GoogleIdentity(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String hostedDomain,
        String picture) {}
