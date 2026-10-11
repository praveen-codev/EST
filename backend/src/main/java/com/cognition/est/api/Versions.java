package com.cognition.est.api;

/** Parses If-Match / ETag values of the form "3" or W/"3". */
final class Versions {
    private Versions() {}

    static Integer parse(String header) {
        if (header == null || header.isBlank() || "*".equals(header.trim())) return null;
        String v = header.trim().replaceFirst("^W/", "").replace("\"", "");
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid If-Match header");
        }
    }

    static String etag(int version) {
        return "\"" + version + "\"";
    }
}
