package com.cognition.est.replication;

import com.cognition.est.app.config.TrackGuard;
import com.cognition.est.app.db.PostgresUrl;
import com.cognition.est.app.db.SchemaVersion;
import com.cognition.est.app.job.JobExit;
import com.cognition.est.audit.AuditRepository;
import com.cognition.est.calendar.EventFingerprint;
import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Candidate;
import com.cognition.est.domain.repo.CandidateRepository;
import com.cognition.est.domain.repo.CatalogRepository;
import com.cognition.est.domain.repo.ClientRepository;
import com.cognition.est.domain.repo.EventRepository;
import com.cognition.est.domain.repo.JsonCodec;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import com.cognition.est.domain.repo.SessionRepository;
import com.cognition.est.domain.repo.SuggestionRepository;
import com.cognition.est.domain.service.EngineContextFactory;
import com.cognition.est.domain.service.SessionOps;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public final class CalendarEvidenceDistributor {
    private static final String DEFAULT_ORG = "org_cognition";
    private static final String SANDBOX = "sandbox";

    private CalendarEvidenceDistributor() {}

    /** Advisory lock key ("ESTREPL") serialising replications into one sandbox database. */
    static final long LOCK_KEY = 0x4553545245504CL;

    public static void main(String[] args) {
        if (LoggerFactory.getLogger("org.flywaydb") instanceof ch.qos.logback.classic.Logger flyway)
            flyway.setLevel(ch.qos.logback.classic.Level.WARN);
        System.exit(run(System.getenv()));
    }

    /**
     * Runs one replication from environment values and returns a {@link JobExit} code. Output is
     * counts only; failures report the exception type, never row data or connection details.
     */
    static int run(Map<String, String> env) {
        try {
            return replicateFromEnv(env);
        } catch (JobExit.Refused e) {
            System.err.println("Calendar evidence replication refused: " + e.getMessage());
            return JobExit.REFUSED;
        } catch (Exception e) {
            System.err.println(
                    "Calendar evidence replication failed: " + e.getClass().getSimpleName());
            return JobExit.ERROR;
        }
    }

    private static int replicateFromEnv(Map<String, String> env) throws SQLException {
        PostgresUrl source = PostgresUrl.parse(required(env, "EST_SOURCE_DATABASE_URL"));
        PostgresUrl target = PostgresUrl.parse(required(env, "EST_SANDBOX_DATABASE_URL"));
        if (source.jdbcUrl().equals(target.jdbcUrl()))
            throw new JobExit.Refused("source and sandbox URLs point at the same database");
        String organizationId = env.getOrDefault("EST_REPLICATION_ORG", DEFAULT_ORG);
        int lookbackDays =
                Integer.parseInt(env.getOrDefault("EST_REPLICATION_LOOKBACK_DAYS", "30"));
        boolean dryRun = Boolean.parseBoolean(env.getOrDefault("EST_REPLICATION_DRY_RUN", "true"));
        Instant cutoff = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);

        try (Connection read = source.readOnly().connect();
                Connection write = target.connect()) {
            SchemaVersion.requireCurrent(new SingleConnectionDataSource(read, true), "source");
            SchemaVersion.requireCurrent(new SingleConnectionDataSource(write, true), "sandbox");
            beginReadOnlySnapshot(read);
            write.setAutoCommit(false);
            if (!tryLock(write)) {
                write.rollback();
                read.rollback();
                System.out.println(
                        "Calendar evidence replication busy: another run holds the lock");
                return JobExit.BUSY;
            }
            Map<String, Integer> counts = replicate(read, write, organizationId, cutoff);
            read.rollback();
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
            return JobExit.OK;
        }
    }

    /**
     * Pins the source connection to one read-only, repeatable-read transaction and verifies the
     * server agrees. Defence in depth only: the source role must still lack write privileges.
     */
    static void beginReadOnlySnapshot(Connection read) throws SQLException {
        read.setAutoCommit(false);
        read.setReadOnly(true);
        read.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        try (Statement statement = read.createStatement()) {
            statement.execute("set session characteristics as transaction read only");
            try (ResultSet rs = statement.executeQuery("show transaction_read_only")) {
                if (!rs.next() || !"on".equalsIgnoreCase(rs.getString(1)))
                    throw new JobExit.Refused("source connection is not read-only");
            }
        }
    }

    /** Transaction-scoped advisory lock on the sandbox; released on commit or rollback. */
    static boolean tryLock(Connection write) throws SQLException {
        try (PreparedStatement statement =
                write.prepareStatement("select pg_try_advisory_xact_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Copies production evidence into the sandbox. Members and identities are matched by Google
     * subject or e-mail so sandbox-provisioned IDs keep working; proposals are generated by the
     * sandbox's own engine instead of copying production decisions.
     */
    static Map<String, Integer> replicate(
            Connection source, Connection target, String organizationId, Instant cutoff)
            throws SQLException {
        Instant now = Instant.now();
        JdbcClient sandbox = JdbcClient.create(new SingleConnectionDataSource(target, true));
        verifyTracks(source, target, sandbox, now);
        Map<String, Integer> counts = new LinkedHashMap<>();
        OffsetDateTime since = cutoff.atOffset(ZoneOffset.UTC);
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
                        true,
                        Set.of(),
                        row -> row));
        Map<String, String> memberIds = new HashMap<>();
        int[] memberConflicts = {0};
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
                        Set.of("role", "status", "created_at"),
                        row -> {
                            String id = sandboxMemberId(target, organizationId, row);
                            if (id == null) {
                                memberConflicts[0]++;
                                return null;
                            }
                            memberIds.put((String) row.get("id"), id);
                            row.put("id", id);
                            return row;
                        }));
        counts.put("member_conflicts", memberConflicts[0]);
        Map<String, String> identityIds = new HashMap<>();
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
                        true,
                        Set.of("created_at"),
                        row -> {
                            String member = memberIds.get((String) row.get("member_id"));
                            if (member == null) return null;
                            String sourceId = (String) row.get("id");
                            row.put("member_id", member);
                            String existing =
                                    first(
                                            target,
                                            """
                                            select id from member_identities
                                            where member_id = ? and provider = ?
                                              and lower(email) = lower(?)
                                            """,
                                            member,
                                            row.get("provider"),
                                            row.get("email"));
                            if (existing != null) row.put("id", existing);
                            identityIds.put(sourceId, (String) row.get("id"));
                            return row;
                        }));
        for (String table : List.of("clients", "teams", "people"))
            counts.put(
                    table,
                    copy(
                            source,
                            target,
                            table,
                            "select * from " + table + " where organization_id = ?",
                            List.of(organizationId),
                            List.of("id"),
                            false,
                            Set.of(),
                            row -> row));
        counts.put(
                "catalog",
                copy(
                        source,
                        target,
                        "catalog_items",
                        "select * from catalog_items where organization_id = ?",
                        List.of(organizationId),
                        List.of("organization_id", "id"),
                        false,
                        Set.of(),
                        row -> row));
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
                        false,
                        Set.of(),
                        row -> row));
        Map<String, String> eventIds = new LinkedHashMap<>();
        Map<String, String> previousEtags = new HashMap<>();
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
                        order by start_at, id
                        """,
                        List.of(organizationId, since),
                        List.of("id"),
                        true,
                        Set.of("created_at"),
                        row -> {
                            String owner = memberIds.get((String) row.get("owner_member_id"));
                            String sourceIdentity = (String) row.get("identity_id");
                            String identity =
                                    sourceIdentity == null ? null : identityIds.get(sourceIdentity);
                            if (owner == null || (sourceIdentity != null && identity == null))
                                return null;
                            String sourceId = (String) row.get("id");
                            row.put("owner_member_id", owner);
                            row.put("identity_id", identity);
                            String existing =
                                    identity == null
                                            ? null
                                            : first(
                                                    target,
                                                    """
                                                    select id from calendar_events
                                                    where identity_id = ? and provider_event_id = ?
                                                    """,
                                                    identity,
                                                    row.get("provider_event_id"));
                            if (existing != null) row.put("id", existing);
                            String id = (String) row.get("id");
                            String etag =
                                    first(
                                            target,
                                            "select coalesce(etag, '') from calendar_events where id = ?",
                                            id);
                            if (etag != null) previousEtags.put(id, etag);
                            eventIds.put(sourceId, id);
                            return row;
                        }));
        try (PreparedStatement deleteAttendees =
                target.prepareStatement(
                        "delete from calendar_event_attendees where event_id = ?")) {
            for (String event : new LinkedHashSet<>(eventIds.values())) {
                deleteAttendees.setString(1, event);
                deleteAttendees.addBatch();
            }
            deleteAttendees.executeBatch();
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
                        List.of(organizationId, since),
                        List.of("event_id", "email"),
                        true,
                        Set.of(),
                        row -> {
                            String event = eventIds.get((String) row.get("event_id"));
                            if (event == null) return null;
                            row.put("event_id", event);
                            return row;
                        }));
        counts.putAll(propose(sandbox, organizationId, eventIds.values(), previousEtags, now));
        return counts;
    }

    /**
     * Runs the sandbox's suggestion engine over copied evidence: new events get an open candidate
     * with fresh proposals, changed events are re-analysed, unchanged ones keep sandbox decisions.
     */
    private static Map<String, Integer> propose(
            JdbcClient sandbox,
            String organizationId,
            Collection<String> eventIds,
            Map<String, String> previousEtags,
            Instant now) {
        JsonCodec json = new JsonCodec();
        EventRepository events = new EventRepository(sandbox);
        CandidateRepository candidates = new CandidateRepository(sandbox, json);
        SuggestionRepository suggestions = new SuggestionRepository(sandbox, json);
        SessionRepository sessions = new SessionRepository(sandbox, json);
        ClientRepository clients = new ClientRepository(sandbox);
        PersonRepository people = new PersonRepository(sandbox);
        EngineContextFactory contexts =
                new EngineContextFactory(
                        new MemberRepository(sandbox),
                        clients,
                        people,
                        new CatalogRepository(sandbox),
                        sessions);
        SessionOps ops =
                new SessionOps(
                        events,
                        candidates,
                        suggestions,
                        sessions,
                        clients,
                        people,
                        contexts,
                        json,
                        new AuditRepository(sandbox, json));
        int created = 0;
        int reanalyzed = 0;
        for (String eventId : new LinkedHashSet<>(eventIds)) {
            CalendarEvent event = events.findById(eventId).orElseThrow();
            Optional<Candidate> candidate = candidates.findByEvent(eventId);
            if (candidate.isEmpty()) {
                ops.createCandidate(
                        organizationId,
                        event,
                        EventFingerprint.candidateIdFor(
                                event.ownerMemberId(), event.providerEventId()),
                        now);
                created++;
            } else if (!Objects.equals(
                    previousEtags.get(eventId), Objects.requireNonNullElse(event.etag(), ""))) {
                ops.reanalyzeCandidate(organizationId, candidate.get(), event, now);
                reanalyzed++;
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("candidates", created);
        counts.put("reanalyzed", reanalyzed);
        return counts;
    }

    /**
     * The sandbox member for a production member: matched by Google subject or e-mail, else the
     * production ID. Null when the keys point at different sandbox members, a different Google
     * account owns the e-mail, or the match belongs to another organization; such members are
     * skipped rather than merged.
     */
    private static String sandboxMemberId(
            Connection target, String organizationId, Map<String, Object> member)
            throws SQLException {
        String sub = (String) member.get("google_sub");
        String id = null;
        try (PreparedStatement select =
                target.prepareStatement(
                        """
                        select id, organization_id, google_sub from members
                        where (google_sub is not null and google_sub = ?)
                           or (organization_id = ? and lower(email) = lower(?))
                           or id = ?
                        """)) {
            bind(select, Arrays.asList(sub, organizationId, member.get("email"), member.get("id")));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    if (!organizationId.equals(rows.getString("organization_id"))) return null;
                    String existingSub = rows.getString("google_sub");
                    if (id != null && !id.equals(rows.getString("id"))) return null;
                    if (sub != null && existingSub != null && !sub.equals(existingSub)) return null;
                    id = rows.getString("id");
                }
            }
        }
        return id == null ? (String) member.get("id") : id;
    }

    private static void verifyTracks(
            Connection source, Connection target, JdbcClient sandbox, Instant now)
            throws SQLException {
        String from = track(source);
        if (from != null && !TrackGuard.PROD.equals(from))
            throw new JobExit.Refused("Replication source is the " + from + " track");
        String to = track(target);
        if (to != null && !SANDBOX.equals(to))
            throw new JobExit.Refused("Replication target is the " + to + " track");
        if (to == null && hasTrackTable(target)) TrackGuard.claim(sandbox, SANDBOX, now);
    }

    private static boolean hasTrackTable(Connection db) throws SQLException {
        return first(db, "select to_regclass('deployment_track')::text") != null;
    }

    private static String track(Connection db) throws SQLException {
        return hasTrackTable(db)
                ? first(db, "select track from deployment_track where id = 1")
                : null;
    }

    private static String first(Connection db, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement select = db.prepareStatement(sql)) {
            bind(select, Arrays.asList(parameters));
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    @FunctionalInterface
    private interface RowRewrite {
        /** Returns the row to write (possibly with remapped keys), or null to skip it. */
        Map<String, Object> apply(Map<String, Object> row) throws SQLException;
    }

    private static int copy(
            Connection source,
            Connection target,
            String table,
            String selectSql,
            List<Object> parameters,
            List<String> conflictColumns,
            boolean updateOnConflict,
            Set<String> targetOwnedColumns,
            RowRewrite rewrite)
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
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columns.size(); i++) {
                            row.put(columns.get(i - 1), rows.getObject(i));
                        }
                        Map<String, Object> written = rewrite.apply(row);
                        if (written == null) continue;
                        bind(insert, new ArrayList<>(written.values()));
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

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
