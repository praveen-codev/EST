package com.cognition.est.app.web;

import java.time.Instant;
import java.util.List;

/** Uniform error body (RFC 9457 style). */
public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        List<FieldError> errors) {

    public record FieldError(String field, String message) {}
}
