package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Attendee;
import com.cognition.est.domain.model.CalendarEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {
    private final JdbcClient jdbc;

    public EventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT =
            """
            select id, identity_id, owner_member_id, provider_event_id, recurring_event_id, title, description, location, conferencing,
                   start_at, end_at, time_zone, organizer_email, etag, content_hash, status from calendar_events e
            """;

    private List<CalendarEvent> withAttendees(List<Object[]> rows) {
        if (rows.isEmpty()) return List.of();
        Map<String, List<Attendee>> att = new LinkedHashMap<>();
        List<String> ids = rows.stream().map(r -> (String) r[0]).toList();
        jdbc.sql(
                        "select event_id, email, display_name, response_status, organizer from calendar_event_attendees where event_id in (:ids) order by email")
                .param("ids", ids)
                .query(
                        (rs, i) ->
                                att.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                                        .add(
                                                new Attendee(
                                                        rs.getString(2),
                                                        rs.getString(3),
                                                        rs.getString(4),
                                                        rs.getBoolean(5))))
                .list();
        return rows.stream()
                .map(
                        r ->
                                new CalendarEvent(
                                        (String) r[0],
                                        (String) r[1],
                                        (String) r[2],
                                        (String) r[3],
                                        (String) r[4],
                                        (String) r[5],
                                        (String) r[6],
                                        (String) r[7],
                                        (String) r[8],
                                        (Instant) r[9],
                                        (Instant) r[10],
                                        (String) r[11],
                                        (String) r[12],
                                        List.copyOf(att.getOrDefault((String) r[0], List.of())),
                                        (String) r[13],
                                        (String) r[14],
                                        (String) r[15]))
                .toList();
    }

    private static Object[] row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Object[] {
            rs.getString("id"),
            rs.getString("identity_id"),
            rs.getString("owner_member_id"),
            rs.getString("provider_event_id"),
            rs.getString("recurring_event_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("location"),
            rs.getString("conferencing"),
            Rows.instant(rs, "start_at"),
            Rows.instant(rs, "end_at"),
            rs.getString("time_zone"),
            rs.getString("organizer_email"),
            rs.getString("etag"),
            rs.getString("content_hash"),
            rs.getString("status")
        };
    }

    public List<CalendarEvent> findByOwner(String ownerMemberId) {
        return withAttendees(
                jdbc.sql(SELECT + " where owner_member_id = :m order by start_at")
                        .param("m", ownerMemberId)
                        .query((rs, i) -> row(rs))
                        .list());
    }

    public List<CalendarEvent> findByOrg(String orgId) {
        return withAttendees(
                jdbc.sql(SELECT + " where organization_id = :org order by start_at")
                        .param("org", orgId)
                        .query((rs, i) -> row(rs))
                        .list());
    }

    public Optional<CalendarEvent> findById(String id) {
        return withAttendees(
                        jdbc.sql(SELECT + " where id = :id")
                                .param("id", id)
                                .query((rs, i) -> row(rs))
                                .list())
                .stream()
                .findFirst();
    }

    public List<CalendarEvent> findByIds(List<String> ids) {
        if (ids.isEmpty()) return List.of();
        return withAttendees(
                jdbc.sql(SELECT + " where id in (:ids) order by start_at")
                        .param("ids", ids)
                        .query((rs, i) -> row(rs))
                        .list());
    }

    public Optional<CalendarEvent> findByProviderId(String identityId, String providerEventId) {
        return withAttendees(
                        jdbc.sql(SELECT + " where identity_id = :i and provider_event_id = :p")
                                .param("i", identityId)
                                .param("p", providerEventId)
                                .query((rs, i) -> row(rs))
                                .list())
                .stream()
                .findFirst();
    }

    public void upsert(String orgId, CalendarEvent e, String raw, String runId, Instant now) {
        int updated =
                jdbc.sql(
                                """
                update calendar_events set recurring_event_id = :rec, title = :title, description = :desc, location = :loc, conferencing = :conf, start_at = :start, end_at = :end,
                       time_zone = :tz, organizer_email = :org_email, etag = :etag, content_hash = :hash, status = :status, raw = :raw, last_seen_run_id = :run, updated_at = :now
                 where id = :id
                """)
                        .param("rec", e.recurringEventId())
                        .param("title", e.title())
                        .param("desc", e.description())
                        .param("loc", e.location())
                        .param("conf", e.conferencing())
                        .param("start", e.start())
                        .param("end", e.end())
                        .param("tz", e.timeZone())
                        .param("org_email", e.organizerEmail())
                        .param("etag", e.etag())
                        .param("hash", e.contentHash())
                        .param("status", e.status())
                        .param("raw", raw)
                        .param("run", runId)
                        .param("now", now)
                        .param("id", e.id())
                        .update();
        if (updated == 0) {
            jdbc.sql(
                            """
                    insert into calendar_events (id, organization_id, identity_id, owner_member_id, provider_event_id, recurring_event_id, title, description, location, conferencing,
                                                 start_at, end_at, time_zone, organizer_email, etag, content_hash, status, raw, first_seen_run_id, last_seen_run_id, created_at, updated_at)
                    values (:id, :org, :identity, :owner, :pid, :rec, :title, :desc, :loc, :conf, :start, :end, :tz, :org_email, :etag, :hash, :status, :raw, :run, :run, :now, :now)
                    """)
                    .param("id", e.id())
                    .param("org", orgId)
                    .param("identity", e.identityId())
                    .param("owner", e.ownerMemberId())
                    .param("pid", e.providerEventId())
                    .param("rec", e.recurringEventId())
                    .param("title", e.title())
                    .param("desc", e.description())
                    .param("loc", e.location())
                    .param("conf", e.conferencing())
                    .param("start", e.start())
                    .param("end", e.end())
                    .param("tz", e.timeZone())
                    .param("org_email", e.organizerEmail())
                    .param("etag", e.etag())
                    .param("hash", e.contentHash())
                    .param("status", e.status())
                    .param("raw", raw)
                    .param("run", runId)
                    .param("now", now)
                    .update();
        }
        jdbc.sql("delete from calendar_event_attendees where event_id = :id")
                .param("id", e.id())
                .update();
        for (Attendee a : e.attendees()) {
            jdbc.sql(
                            "insert into calendar_event_attendees (event_id, email, display_name, response_status, organizer) values (:e, :email, :name, :rs, :org)")
                    .param("e", e.id())
                    .param("email", a.email().toLowerCase())
                    .param("name", a.name())
                    .param("rs", a.responseStatus() == null ? "needsAction" : a.responseStatus())
                    .param("org", a.organizer())
                    .update();
        }
    }

    public void deleteByOwner(String ownerMemberId) {
        jdbc.sql("delete from calendar_events where owner_member_id = :m")
                .param("m", ownerMemberId)
                .update();
    }
}
