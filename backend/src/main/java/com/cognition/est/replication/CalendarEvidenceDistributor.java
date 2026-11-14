package com.cognition.est.replication;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CalendarEvidenceDistributor {
    private static final String DEFAULT_ORG = "org_cognition";

    private CalendarEvidenceDistributor() {}

    public static void main(String[] args) throws Exception {
        Database source = Database.parse(required("EST_SOURCE_DATABASE_URL"));
        Database target = Database.parse(required("EST_SANDBOX_DATABASE_URL"));
        String organizationId = System.getenv().getOrDefault("EST_REPLICATION_ORG", DEFAULT_ORG);
        int lookbackDays =
                Integer.parseInt(
                        System.getenv().getOrDefault("EST_REPLICATION_LOOKBACK_DAYS", "30"));
        boolean dryRun =
                Boolean.parseBoolean(
                        System.getenv().getOrDefault("EST_REPLICATION_DRY_RUN", "true"));
        Instant cutoff = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);

        try (Connection read = source.connect();
                Connection write = target.connect()) {
            read.setReadOnly(true);
            write.setAutoCommit(false);
            Map<String, Integer> counts = replicate(read, write, organizationId, cutoff);
            if (dryRun) {
                write.rollback();
            } else {
                write.commit();
            }
            System.out.printf(
                    "Calendar evidence replication %s for %s from %s: %s%n",
                    dryRun ? "dry run" : "completed",
                    organizationId,
                    cutoff,
                    counts.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining(", ")));
        }
    }

    private static Map<String, Integer> replicate(
            Connection source, Connection target, String organizationId, Instant cutoff)
            throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        verifyMemberIds(source, target, organizationId);
        counts.put(
                "organizations",
                copy(
                        source,
                        target,
                        "organizations",
                        """
                        select id, name, allowed_domains, created_at, updated_at
                        from organizations where id = ?
                        """,
                        List.of(organizationId),
                        List.of("id"),
                        true));
        counts.put(
                "members",
                copy(
                        source,
                        target,
                        "members",
                        """
                        select id, organization_id, google_sub, email, name, title, role, status,
                               hue, time_zone, last_sign_in_at, created_at, updated_at
                        from members where organization_id = ?
                        """,
                        List.of(organizationId),
                        List.of("id"),
                        true,
                        Set.of("role", "status")));
        counts.put(
                "identities",
                copy(
                        source,
                        target,
                        "member_identities",
                        """
                        select id, organization_id, member_id, provider, provider_sub, email,
                               display_name, scopes, null::text as refresh_token_enc,
                               null::varchar as refresh_token_iv, null::text as sync_token,
                               status, last_sync_at, null::text as last_error, created_at, updated_at
                        from member_identities where organization_id = ?
                        """,
                        List.of(organizationId),
                        List.of("id"),
                        true));
        counts.put(
                "catalog",
                copy(
                        source,
                        target,
                        "catalog_items",
                        "select * from catalog_items where organization_id = ?",
                        List.of(organizationId),
                        List.of("organization_id", "id"),
                        false));
        counts.put(
                "clients",
                copy(
                        source,
                        target,
                        "clients",
                        "select * from clients where organization_id = ?",
                        List.of(organizationId),
                        List.of("id"),
                        false));
        counts.put(
                "client_domains",
                copy(
                        source,
                        target,
                        "client_domains",
                        """
                        select d.* from client_domains d
                        join clients c on c.id = d.client_id
                        where c.organization_id = ?
                        """,
                        List.of(organizationId),
                        List.of("client_id", "domain"),
                        false));
        counts.put(
                "teams",
                copy(
                        source,
                        target,
                        "teams",
                        "select * from teams where organization_id = ?",
                        List.of(organizationId),
                        List.of("id"),
                        false));
        counts.put(
                "people",
                copy(
                        source,
                        target,
                        "people",
                        "select * from people where organization_id = ?",
                        List.of(organizationId),
                        List.of("id"),
                        false));
        counts.put(
                "events",
                copy(
                        source,
                        target,
                        "calendar_events",
                        """
                        select id, organization_id, identity_id, owner_member_id, provider_event_id,
                               recurring_event_id, title, description, location, conferencing,
                               start_at, end_at, time_zone, organizer_email, etag, content_hash,
                               status, null::jsonb as raw, null::varchar as first_seen_run_id,
                               null::varchar as last_seen_run_id, created_at, updated_at
                        from calendar_events
                        where organization_id = ? and end_at >= ?
                        """,
                        List.of(organizationId, cutoff.atOffset(ZoneOffset.UTC)),
                        List.of("id"),
                        true));
        try (PreparedStatement deleteAttendees =
                target.prepareStatement(
                        """
                        delete from calendar_event_attendees
                        where event_id in (
                            select id from calendar_events
                            where organization_id = ? and end_at >= ?
                        )
                        """)) {
            bind(deleteAttendees, List.of(organizationId, cutoff.atOffset(ZoneOffset.UTC)));
            deleteAttendees.executeUpdate();
        }
        counts.put(
                "attendees",
                copy(
                        source,
                        target,
                        "calendar_event_attendees",
                        """
                        select a.* from calendar_event_attendees a
                        join calendar_events e on e.id = a.event_id
                        where e.organization_id = ? and e.end_at >= ?
                        """,
                        List.of(organizationId, cutoff.atOffset(ZoneOffset.UTC)),
                        List.of("event_id", "email"),
                        true));
        counts.put(
                "candidates",
                copy(
                        source,
                        target,
                        "candidates",
                        """
                        select c.id, c.organization_id, c.event_id, c.owner_member_id, c.score,
                               c.bucket, c.reasons, c.duplicate_of_candidate_id,
                               'open'::varchar as status, null::varchar as session_id,
                               c.redacted_text, c.redactions, c.created_at, c.updated_at
                        from candidates c
                        join calendar_events e on e.id = c.event_id
                        where c.organization_id = ? and e.end_at >= ?
                        """,
                        List.of(organizationId, cutoff.atOffset(ZoneOffset.UTC)),
                        List.of("id"),
                        true,
                        Set.of("status", "session_id", "created_at")));
        counts.put(
                "suggestions",
                copy(
                        source,
                        target,
                        "suggestions",
                        """
                        select s.id, s.organization_id, s.candidate_id,
                               null::varchar as session_id, s.field, s.value_json, s.label,
                               s.confidence, s.evidence, 'proposed'::varchar as status,
                               s.prompt_version, null::varchar as decided_by,
                               null::timestamptz as decided_at, s.created_at
                        from suggestions s
                        join candidates c on c.id = s.candidate_id
                        join calendar_events e on e.id = c.event_id
                        where s.organization_id = ? and e.end_at >= ?
                          and s.status <> 'superseded'
                        """,
                        List.of(organizationId, cutoff.atOffset(ZoneOffset.UTC)),
                        List.of("id"),
                        false));
        counts.put("superseded", supersede(source, target, organizationId, cutoff));
        counts.put("attached", attachToConfirmedSessions(target, organizationId));
        return counts;
    }

    private static int supersede(
            Connection source, Connection target, String organizationId, Instant cutoff)
            throws SQLException {
        try (PreparedStatement select =
                        source.prepareStatement(
                                """
                                select s.id from suggestions s
                                join candidates c on c.id = s.candidate_id
                                join calendar_events e on e.id = c.event_id
                                where s.organization_id = ? and e.end_at >= ?
                                  and s.status = 'superseded'
                                """);
                PreparedStatement update =
                        target.prepareStatement(
                                """
                                update suggestions set status = 'superseded'
                                where id = ? and organization_id = ? and status = 'proposed'
                                """)) {
            bind(select, List.of(organizationId, cutoff.atOffset(ZoneOffset.UTC)));
            int count = 0;
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    bind(update, List.of(rows.getString("id"), organizationId));
                    count += update.executeUpdate();
                }
            }
            return count;
        }
    }

    private static int attachToConfirmedSessions(Connection target, String organizationId)
            throws SQLException {
        try (PreparedStatement manual =
                target.prepareStatement(
                        """
                        update suggestions s set status = 'superseded'
                        from candidates c join sessions ss on ss.id = c.session_id
                        where c.id = s.candidate_id and s.organization_id = ?
                          and s.session_id is null and s.status = 'proposed'
                          and ss.provenance ->> s.field = 'manual'
                        """)) {
            bind(manual, List.of(organizationId));
            manual.executeUpdate();
        }
        try (PreparedStatement update =
                target.prepareStatement(
                        """
                        update suggestions s set session_id = c.session_id
                        from candidates c
                        where c.id = s.candidate_id and s.organization_id = ?
                          and s.session_id is null and s.status = 'proposed'
                          and c.session_id is not null
                        """)) {
            bind(update, List.of(organizationId));
            return update.executeUpdate();
        }
    }

    private static void verifyMemberIds(Connection source, Connection target, String organizationId)
            throws SQLException {
        String select =
                """
                select id, google_sub, lower(email) as email
                from members where organization_id = ?
                """;
        try (PreparedStatement sourceMembers = source.prepareStatement(select)) {
            sourceMembers.setString(1, organizationId);
            try (ResultSet members = sourceMembers.executeQuery();
                    PreparedStatement targetMember =
                            target.prepareStatement(
                                    """
                                    select id from members
                                    where organization_id = ?
                                      and (lower(email) = ? or (google_sub is not null and google_sub = ?))
                                    """)) {
                while (members.next()) {
                    String sourceId = members.getString("id");
                    String sub = members.getString("google_sub");
                    targetMember.setString(1, organizationId);
                    targetMember.setString(2, members.getString("email"));
                    targetMember.setString(3, sub == null ? "" : sub);
                    try (ResultSet existing = targetMember.executeQuery()) {
                        if (existing.next() && !sourceId.equals(existing.getString("id"))) {
                            throw new IllegalStateException(
                                    "Sandbox member identity conflicts with production for "
                                            + members.getString("email"));
                        }
                    }
                }
            }
        }
    }

    private static int copy(
            Connection source,
            Connection target,
            String table,
            String selectSql,
            List<Object> parameters,
            List<String> conflictColumns,
            boolean updateOnConflict)
            throws SQLException {
        return copy(
                source,
                target,
                table,
                selectSql,
                parameters,
                conflictColumns,
                updateOnConflict,
                Set.of());
    }

    private static int copy(
            Connection source,
            Connection target,
            String table,
            String selectSql,
            List<Object> parameters,
            List<String> conflictColumns,
            boolean updateOnConflict,
            Set<String> targetOwnedColumns)
            throws SQLException {
        try (PreparedStatement select = source.prepareStatement(selectSql)) {
            bind(select, parameters);
            try (ResultSet rows = select.executeQuery()) {
                ResultSetMetaData metadata = rows.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    columns.add(metadata.getColumnLabel(i));
                }
                String placeholders =
                        columns.stream().map(column -> "?").collect(Collectors.joining(", "));
                String conflicts = String.join(", ", conflictColumns);
                String action =
                        updateOnConflict
                                ? "do update set "
                                        + columns.stream()
                                                .filter(column -> !conflictColumns.contains(column))
                                                .filter(
                                                        column ->
                                                                !targetOwnedColumns.contains(
                                                                        column))
                                                .map(column -> column + " = excluded." + column)
                                                .collect(Collectors.joining(", "))
                                : "do nothing";
                String insertSql =
                        "insert into "
                                + table
                                + " ("
                                + String.join(", ", columns)
                                + ") values ("
                                + placeholders
                                + ") on conflict ("
                                + conflicts
                                + ") "
                                + action;
                int count = 0;
                try (PreparedStatement insert = target.prepareStatement(insertSql)) {
                    while (rows.next()) {
                        for (int i = 1; i <= columns.size(); i++) {
                            insert.setObject(i, rows.getObject(i));
                        }
                        count += insert.executeUpdate();
                    }
                }
                return count;
            }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private record Database(String jdbcUrl, String username, String password) {
        private static Database parse(String value) {
            URI uri = URI.create(value);
            if (!Set.of("postgres", "postgresql").contains(uri.getScheme())) {
                throw new IllegalArgumentException("Database URL must use postgres or postgresql");
            }
            String[] userInfo = uri.getRawUserInfo().split(":", 2);
            String user = URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8);
            String password =
                    userInfo.length == 2
                            ? URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8)
                            : "";
            String database =
                    uri.getPath() == null || uri.getPath().isBlank()
                            ? "neondb"
                            : uri.getPath().substring(1);
            String jdbc =
                    "jdbc:postgresql://"
                            + uri.getHost()
                            + ":"
                            + (uri.getPort() == -1 ? 5432 : uri.getPort())
                            + "/"
                            + database
                            + "?sslmode=require&stringtype=unspecified";
            return new Database(jdbc, user, password);
        }

        private Connection connect() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
    }
}
