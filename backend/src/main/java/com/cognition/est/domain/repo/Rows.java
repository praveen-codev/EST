package com.cognition.est.domain.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Small ResultSet helpers shared by the repositories. */
final class Rows {
    private Rows() {}

    static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    static Integer integer(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static List<String> pipeList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static String pipeJoin(List<String> values) {
        return values == null ? "" : String.join("|", values);
    }
}
