package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Candidate;
import com.cognition.est.domain.model.Classification;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CandidateRepository {
    private final JdbcClient jdbc;
    private final JsonCodec json;

    public CandidateRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private RowMapper<Candidate> mapper() {
        return (rs, i) ->
                new Candidate(
                        rs.getString("id"),
                        rs.getString("event_id"),
                        rs.getString("owner_member_id"),
                        new Classification(
                                rs.getBigDecimal("score").doubleValue(),
                                rs.getString("bucket"),
                                json.strings(rs.getString("reasons"))),
                        rs.getString("duplicate_of_candidate_id"),
                        rs.getString("status"),
                        rs.getString("session_id"),
                        rs.getString("redacted_text"),
                        json.strings(rs.getString("redactions")),
                        Rows.instant(rs, "created_at"),
                        Rows.instant(rs, "updated_at"));
    }

    private static final String SELECT =
            "select id, event_id, owner_member_id, score, bucket, reasons, duplicate_of_candidate_id, status, session_id, redacted_text, redactions, created_at, updated_at from candidates";

    public List<Candidate> findByOwner(String ownerMemberId) {
        return jdbc.sql(SELECT + " where owner_member_id = :m")
                .param("m", ownerMemberId)
                .query(mapper())
                .list();
    }

    public List<Candidate> findByOrg(String orgId) {
        return jdbc.sql(SELECT + " where organization_id = :org")
                .param("org", orgId)
                .query(mapper())
                .list();
    }

    public Optional<Candidate> findById(String orgId, String id) {
        return jdbc.sql(SELECT + " where organization_id = :org and id = :id")
                .param("org", orgId)
                .param("id", id)
                .query(mapper())
                .optional();
    }

    public Optional<Candidate> findByEvent(String eventId) {
        return jdbc.sql(SELECT + " where event_id = :e")
                .param("e", eventId)
                .query(mapper())
                .optional();
    }

    public Optional<Candidate> findBySession(String sessionId) {
        return jdbc.sql(SELECT + " where session_id = :s")
                .param("s", sessionId)
                .query(mapper())
                .optional();
    }

    public void upsert(String orgId, Candidate c) {
        int n =
                jdbc.sql(
                                """
                update candidates set score = :score, bucket = :bucket, reasons = :reasons, duplicate_of_candidate_id = :dup, status = :status, session_id = :session,
                       redacted_text = :text, redactions = :red, updated_at = :updated where id = :id
                """)
                        .param("score", BigDecimal.valueOf(c.classification().score()))
                        .param("bucket", c.classification().bucket())
                        .param("reasons", json.write(c.classification().reasons()))
                        .param("dup", c.duplicateOfCandidateId())
                        .param("status", c.status())
                        .param("session", c.sessionId())
                        .param("text", c.redactedText())
                        .param("red", json.write(c.redactions()))
                        .param("updated", c.updatedAt())
                        .param("id", c.id())
                        .update();
        if (n == 0) {
            jdbc.sql(
                            """
                    insert into candidates (id, organization_id, event_id, owner_member_id, score, bucket, reasons, duplicate_of_candidate_id, status, session_id, redacted_text, redactions, created_at, updated_at)
                    values (:id, :org, :event, :owner, :score, :bucket, :reasons, :dup, :status, :session, :text, :red, :created, :updated)
                    """)
                    .param("id", c.id())
                    .param("org", orgId)
                    .param("event", c.eventId())
                    .param("owner", c.ownerMemberId())
                    .param("score", BigDecimal.valueOf(c.classification().score()))
                    .param("bucket", c.classification().bucket())
                    .param("reasons", json.write(c.classification().reasons()))
                    .param("dup", c.duplicateOfCandidateId())
                    .param("status", c.status())
                    .param("session", c.sessionId())
                    .param("text", c.redactedText())
                    .param("red", json.write(c.redactions()))
                    .param("created", c.createdAt())
                    .param("updated", c.updatedAt())
                    .update();
        }
    }

    public void setStatus(String id, String status, String sessionId, Instant now) {
        jdbc.sql(
                        "update candidates set status = :s, session_id = :session, updated_at = :now where id = :id")
                .param("s", status)
                .param("session", sessionId)
                .param("now", now)
                .param("id", id)
                .update();
    }

    public void deleteByOwner(String ownerMemberId) {
        jdbc.sql("delete from candidates where owner_member_id = :m")
                .param("m", ownerMemberId)
                .update();
    }
}
