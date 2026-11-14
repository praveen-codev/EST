package com.cognition.est.app.db;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** JDBC values that are portable across H2 and PostgreSQL. */
public final class JdbcValues {
    private JdbcValues() {}

    public static OffsetDateTime timestamp(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
