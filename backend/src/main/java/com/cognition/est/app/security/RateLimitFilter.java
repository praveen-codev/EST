package com.cognition.est.app.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket limits on the endpoints that are either unauthenticated or expensive: sign-in and
 * refresh (per client IP), calendar sync (per bearer token), dev endpoints. Everything else relies
 * on authentication. Buckets live in memory per node; enough for one API instance.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String prefix, long capacity, Duration period) {}

    private static final Rule[] RULES = {
        new Rule("/api/v1/auth/", 30, Duration.ofMinutes(1)),
        new Rule("/api/v1/calendar/sync", 10, Duration.ofMinutes(1)),
        new Rule("/api/v1/calendar/identities/link/", 20, Duration.ofMinutes(1)),
        new Rule("/api/v1/dev/", 120, Duration.ofMinutes(1)),
    };

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        for (Rule rule : RULES) {
            if (!path.startsWith(rule.prefix())) continue;
            String key = rule.prefix() + "|" + clientKey(req);
            Bucket bucket =
                    buckets.computeIfAbsent(
                            key,
                            k ->
                                    Bucket.builder()
                                            .addLimit(
                                                    Bandwidth.builder()
                                                            .capacity(rule.capacity())
                                                            .refillGreedy(
                                                                    rule.capacity(), rule.period())
                                                            .build())
                                            .build());
            if (!bucket.tryConsume(1)) {
                res.setStatus(429);
                res.setHeader("Retry-After", String.valueOf(rule.period().toSeconds()));
                res.setContentType("application/json");
                res.getWriter()
                        .write(
                                "{\"type\":\"about:blank\",\"title\":\"Too many requests\",\"status\":429,\"detail\":\"Slow down and retry shortly.\"}");
                return;
            }
            break;
        }
        chain.doFilter(req, res);
    }

    /**
     * Bearer token hash when present (per member), otherwise the client IP (behind a proxy:
     * X-Forwarded-For first hop).
     */
    private static String clientKey(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer "))
            return "t:" + Integer.toHexString(auth.hashCode());
        String fwd = req.getHeader("X-Forwarded-For");
        return "ip:"
                + (fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : req.getRemoteAddr());
    }
}
