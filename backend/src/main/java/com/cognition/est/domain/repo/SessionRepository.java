package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.model.SessionContact;
import com.cognition.est.domain.model.UseCase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Sessions with their child collections. Writes replace the child rows wholesale (small sets, one
 * transaction).
 */
@Repository
public class SessionRepository {
    private final JdbcClient jdbc;
    private final JsonCodec json;

    public SessionRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static final String SELECT =
            """
            select id, state, title, client_id, team_id, facilitator_id, scheduled_start, scheduled_end, actual_start, actual_end, participants_estimated,
                   session_type_id, outcome_id, summary, notes, provenance, version, created_by, created_at, updated_by, updated_at from sessions
            """;

    private record Core(
            String id,
            String state,
            String title,
            String clientId,
            String teamId,
            String facilitatorId,
            Instant scheduledStart,
            Instant scheduledEnd,
            Instant actualStart,
            Instant actualEnd,
            Integer participantsEstimated,
            String sessionType,
            String outcomeId,
            String summary,
            String notes,
            Map<String, String> provenance,
            int version,
            String createdBy,
            Instant createdAt,
            String updatedBy,
            Instant updatedAt) {}

    private Core core(ResultSet rs) throws SQLException {
        return new Core(
                rs.getString("id"),
                rs.getString("state"),
                rs.getString("title"),
                rs.getString("client_id"),
                rs.getString("team_id"),
                rs.getString("facilitator_id"),
                Rows.instant(rs, "scheduled_start"),
                Rows.instant(rs, "scheduled_end"),
                Rows.instant(rs, "actual_start"),
                Rows.instant(rs, "actual_end"),
                Rows.integer(rs, "participants_estimated"),
                rs.getString("session_type_id"),
                rs.getString("outcome_id"),
                rs.getString("summary"),
                rs.getString("notes"),
                json.stringMap(rs.getString("provenance")),
                rs.getInt("version"),
                rs.getString("created_by"),
                Rows.instant(rs, "created_at"),
                rs.getString("updated_by"),
                Rows.instant(rs, "updated_at"));
    }

    private List<Session> hydrate(List<Core> cores) {
        if (cores.isEmpty()) return List.of();
        List<String> ids = cores.stream().map(Core::id).toList();
        Map<String, List<String>> cofac =
                group(
                        "select session_id, member_id from session_co_facilitators where session_id in (:ids)",
                        ids);
        Map<String, List<String>> participants =
                group(
                        "select session_id, person_id from session_participants where session_id in (:ids)",
                        ids);
        Map<String, List<String>> tech =
                group(
                        "select session_id, technology_id from session_technologies where session_id in (:ids)",
                        ids);
        Map<String, List<String>> sources =
                group(
                        "select session_id, event_id from session_source_events where session_id in (:ids)",
                        ids);
        Map<String, List<SessionContact>> contacts = new LinkedHashMap<>();
        jdbc.sql(
                        "select session_id, person_id, role_id from session_contacts where session_id in (:ids)")
                .param("ids", ids)
                .query(
                        (rs, i) ->
                                contacts.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                                        .add(new SessionContact(rs.getString(2), rs.getString(3))))
                .list();
        Map<String, List<String>> ucTech = new LinkedHashMap<>();
        jdbc.sql(
                        "select t.use_case_id, t.technology_id from use_case_technologies t join use_cases u on u.id = t.use_case_id where u.session_id in (:ids)")
                .param("ids", ids)
                .query(
                        (rs, i) ->
                                ucTech.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                                        .add(rs.getString(2)))
                .list();
        Map<String, List<UseCase>> useCases = new LinkedHashMap<>();
        jdbc.sql(
                        "select id, session_id, title, description, use_case_type_id from use_cases where session_id in (:ids) order by sort_order, id")
                .param("ids", ids)
                .query(
                        (rs, i) ->
                                useCases.computeIfAbsent(
                                                rs.getString("session_id"), k -> new ArrayList<>())
                                        .add(
                                                new UseCase(
                                                        rs.getString("id"),
                                                        rs.getString("title"),
                                                        rs.getString("description"),
                                                        rs.getString("use_case_type_id"),
                                                        List.copyOf(
                                                                ucTech.getOrDefault(
                                                                        rs.getString("id"),
                                                                        List.of())))))
                .list();
        List<Session> out = new ArrayList<>();
        for (Core c : cores) {
            out.add(
                    new Session(
                            c.id(),
                            c.state(),
                            c.title(),
                            c.clientId(),
                            c.teamId(),
                            c.facilitatorId(),
                            List.copyOf(cofac.getOrDefault(c.id(), List.of())),
                            List.copyOf(contacts.getOrDefault(c.id(), List.of())),
                            c.scheduledStart(),
                            c.scheduledEnd(),
                            c.actualStart(),
                            c.actualEnd(),
                            c.participantsEstimated(),
                            List.copyOf(participants.getOrDefault(c.id(), List.of())),
                            c.sessionType(),
                            c.outcomeId(),
                            List.copyOf(tech.getOrDefault(c.id(), List.of())),
                            List.copyOf(useCases.getOrDefault(c.id(), List.of())),
                            c.summary(),
                            c.notes(),
                            List.copyOf(sources.getOrDefault(c.id(), List.of())),
                            c.provenance(),
                            c.createdBy(),
                            c.createdAt(),
                            c.updatedBy(),
                            c.updatedAt(),
                            c.version()));
        }
        return out;
    }

    private Map<String, List<String>> group(String sql, List<String> ids) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        jdbc.sql(sql)
                .param("ids", ids)
                .query(
                        (rs, i) ->
                                map.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                                        .add(rs.getString(2)))
                .list();
        return map;
    }

    public List<Session> findByOrg(String orgId) {
        return hydrate(
                jdbc.sql(SELECT + " where organization_id = :org order by scheduled_start")
                        .param("org", orgId)
                        .query((rs, i) -> core(rs))
                        .list());
    }

    public Optional<Session> findById(String orgId, String id) {
        return hydrate(
                        jdbc.sql(SELECT + " where organization_id = :org and id = :id")
                                .param("org", orgId)
                                .param("id", id)
                                .query((rs, i) -> core(rs))
                                .list())
                .stream()
                .findFirst();
    }

    public Optional<Session> findBySourceEvent(String eventId) {
        return hydrate(
                        jdbc.sql(
                                        SELECT
                                                + " where id in (select session_id from session_source_events where event_id = :e)")
                                .param("e", eventId)
                                .query((rs, i) -> core(rs))
                                .list())
                .stream()
                .findFirst();
    }

    public void insert(String orgId, Session s) {
        jdbc.sql(
                        """
                insert into sessions (id, organization_id, state, title, client_id, team_id, facilitator_id, scheduled_start, scheduled_end, actual_start, actual_end, participants_estimated,
                                      session_type_id, outcome_id, summary, notes, provenance, version, created_by, created_at, updated_by, updated_at)
                values (:id, :org, :state, :title, :client, :team, :fac, :ss, :se, :as, :ae, :est, :type, :outcome, :summary, :notes, :prov, :version, :cb, :ca, :ub, :ua)
                """)
                .param("id", s.id())
                .param("org", orgId)
                .param("state", s.state())
                .param("title", s.title())
                .param("client", s.clientId())
                .param("team", s.teamId())
                .param("fac", s.facilitatorId())
                .param("ss", s.scheduledStart())
                .param("se", s.scheduledEnd())
                .param("as", s.actualStart())
                .param("ae", s.actualEnd())
                .param("est", s.participantsEstimated())
                .param("type", s.sessionType())
                .param("outcome", s.outcomeId())
                .param("summary", s.summary())
                .param("notes", s.notes())
                .param("prov", json.write(s.provenance()))
                .param("version", s.version())
                .param("cb", s.createdBy())
                .param("ca", s.createdAt())
                .param("ub", s.updatedBy())
                .param("ua", s.updatedAt())
                .update();
        writeChildren(s);
    }

    /**
     * Optimistic update: the stored version must equal {@code expectedVersion}; the row is written
     * with {@code s.version()}.
     */
    public boolean update(String orgId, Session s, int expectedVersion) {
        int n =
                jdbc.sql(
                                """
                update sessions set state = :state, title = :title, client_id = :client, team_id = :team, facilitator_id = :fac, scheduled_start = :ss, scheduled_end = :se, actual_start = :as,
                       actual_end = :ae, participants_estimated = :est, session_type_id = :type, outcome_id = :outcome, summary = :summary, notes = :notes, provenance = :prov,
                       version = :version, updated_by = :ub, updated_at = :ua
                 where organization_id = :org and id = :id and version = :expected
                """)
                        .param("state", s.state())
                        .param("title", s.title())
                        .param("client", s.clientId())
                        .param("team", s.teamId())
                        .param("fac", s.facilitatorId())
                        .param("ss", s.scheduledStart())
                        .param("se", s.scheduledEnd())
                        .param("as", s.actualStart())
                        .param("ae", s.actualEnd())
                        .param("est", s.participantsEstimated())
                        .param("type", s.sessionType())
                        .param("outcome", s.outcomeId())
                        .param("summary", s.summary())
                        .param("notes", s.notes())
                        .param("prov", json.write(s.provenance()))
                        .param("version", s.version())
                        .param("ub", s.updatedBy())
                        .param("ua", s.updatedAt())
                        .param("org", orgId)
                        .param("id", s.id())
                        .param("expected", expectedVersion)
                        .update();
        if (n == 0) return false;
        for (String t :
                List.of(
                        "session_co_facilitators",
                        "session_contacts",
                        "session_participants",
                        "session_technologies",
                        "session_source_events")) {
            jdbc.sql("delete from " + t + " where session_id = :id").param("id", s.id()).update();
        }
        jdbc.sql("delete from use_cases where session_id = :id").param("id", s.id()).update();
        writeChildren(s);
        return true;
    }

    private void writeChildren(Session s) {
        for (String m : s.coFacilitatorIds())
            jdbc.sql("insert into session_co_facilitators (session_id, member_id) values (:s, :m)")
                    .param("s", s.id())
                    .param("m", m)
                    .update();
        for (SessionContact c : s.contacts())
            jdbc.sql(
                            "insert into session_contacts (session_id, person_id, role_id) values (:s, :p, :r)")
                    .param("s", s.id())
                    .param("p", c.personId())
                    .param("r", c.roleId())
                    .update();
        for (String p : s.participantIds())
            jdbc.sql("insert into session_participants (session_id, person_id) values (:s, :p)")
                    .param("s", s.id())
                    .param("p", p)
                    .update();
        for (String t : s.technologyIds())
            jdbc.sql("insert into session_technologies (session_id, technology_id) values (:s, :t)")
                    .param("s", s.id())
                    .param("t", t)
                    .update();
        for (String e : s.sourceEventIds())
            jdbc.sql("insert into session_source_events (session_id, event_id) values (:s, :e)")
                    .param("s", s.id())
                    .param("e", e)
                    .update();
        int order = 0;
        for (UseCase uc : s.useCases()) {
            jdbc.sql(
                            "insert into use_cases (id, session_id, title, description, use_case_type_id, sort_order) values (:id, :s, :title, :desc, :type, :o)")
                    .param("id", uc.id())
                    .param("s", s.id())
                    .param("title", uc.title())
                    .param("desc", uc.description())
                    .param("type", uc.useCaseTypeId())
                    .param("o", order++)
                    .update();
            for (String t : uc.technologyIds())
                jdbc.sql(
                                "insert into use_case_technologies (use_case_id, technology_id) values (:u, :t)")
                        .param("u", uc.id())
                        .param("t", t)
                        .update();
        }
    }

    public void deleteNonSeed(String orgId) {
        jdbc.sql("delete from sessions where organization_id = :org and id not like 'ses_hist_%'")
                .param("org", orgId)
                .update();
    }
}
