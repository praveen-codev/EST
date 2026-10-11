package com.cognition.est.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * The refresh token lives in an httpOnly cookie scoped to the auth endpoints; a second, readable
 * cookie carries a CSRF nonce that the SPA must echo in the X-CSRF header (double submit).
 */
public final class AuthCookies {
    private AuthCookies() {}

    public static final String REFRESH = "est_refresh";
    public static final String CSRF = "est_csrf";
    public static final String PATH = "/api/v1/auth";

    public static void set(
            HttpServletResponse res,
            String refresh,
            String csrf,
            Instant expiresAt,
            boolean secure) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        res.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(REFRESH, refresh)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Strict")
                        .path(PATH)
                        .maxAge(maxAge)
                        .build()
                        .toString());
        res.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(CSRF, csrf)
                        .httpOnly(false)
                        .secure(secure)
                        .sameSite("Strict")
                        .path(PATH)
                        .maxAge(maxAge)
                        .build()
                        .toString());
    }

    public static void clear(HttpServletResponse res, boolean secure) {
        res.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(REFRESH, "")
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Strict")
                        .path(PATH)
                        .maxAge(0)
                        .build()
                        .toString());
        res.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(CSRF, "")
                        .httpOnly(false)
                        .secure(secure)
                        .sameSite("Strict")
                        .path(PATH)
                        .maxAge(0)
                        .build()
                        .toString());
    }

    public static String cookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) if (name.equals(c.getName())) return c.getValue();
        return null;
    }

    /** Double-submit check: header must equal the readable cookie. Constant-time compare. */
    public static boolean csrfOk(HttpServletRequest req) {
        String cookie = cookie(req, CSRF);
        String header = req.getHeader("X-CSRF");
        return cookie != null
                && header != null
                && java.security.MessageDigest.isEqual(cookie.getBytes(), header.getBytes());
    }
}
