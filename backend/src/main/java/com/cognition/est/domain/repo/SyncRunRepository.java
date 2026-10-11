package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.SyncRun;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SyncRunRepository {
    private final JdbcClient jdbc;

    public SyncRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<SyncRun> MAPPER =
            (rs, i) ->
                    new SyncRun(
                            rs.getString("id"),
                            rs.getString("identity_id"),
                            rs.getString("kind"),
                            Rows.instant(rs, "started_at"),
                            Rows.instant(rs, "finished_at"),
                            rs.getString("status"),
                            rs.getInt("events_seen"),
                            rs.getInt("events_new"),
                            rs.getInt("events_updated"),
                            rs.getInt("events_unchanged"),
                            rs.getInt("candidates_created"),
                            rs.getString("error"));

    private static final String SELECT =
            "select id, identity_id, kind, started_at, finished_at, status, events_seen, events_new, events_updated, events_unchanged, candidates_created, error from calendar_sync_runs";

    public List<SyncRun> findRecentForMember(String memberId, int limit) {
        return jdbc.sql(
                        SELECT
                                + " where identity_id in (select id from member_identities where member_id = :m) order by started_at desc limit :n")
                .param("m", memberId)
                .param("n", limit)
                .query(MAPPER)
                .list();
    }

    public void insert(String orgId, SyncRun r) {
        jdbc.sql(
                        """
                insert into calendar_sync_runs (id, organization_id, identity_id, kind, started_at, finished_at, status, events_seen, events_new, events_updated, events_unchanged, candidates_created, error)
                values (:id, :org, :identity, :kind, :started, :finished, :status, :seen, :new, :updated, :unchanged, :created, :error)
                """)
                .param("id", r.id())
                .param("org", orgId)
                .param("identity", r.identityId())
                .param("kind", r.kind())
                .param("started", r.startedAt())
                .param("finished", r.finishedAt())
                .param("status", r.status())
                .param("seen", r.eventsSeen())
                .param("new", r.eventsNew())
                .param("updated", r.eventsUpdated())
                .param("unchanged", r.eventsUnchanged())
                .param("created", r.candidatesCreated())
                .param("error", r.error())
                .update();
    }

    public void finish(SyncRun r) {
        jdbc.sql(
                        "update calendar_sync_runs set finished_at = :finished, status = :status, events_seen = :seen, events_new = :new, events_updated = :updated, events_unchanged = :unchanged, candidates_created = :created, error = :error where id = :id")
                .param("finished", r.finishedAt())
                .param("status", r.status())
                .param("seen", r.eventsSeen())
                .param("new", r.eventsNew())
                .param("updated", r.eventsUpdated())
                .param("unchanged", r.eventsUnchanged())
                .param("created", r.candidatesCreated())
                .param("error", r.error())
                .param("id", r.id())
                .update();
    }
}
