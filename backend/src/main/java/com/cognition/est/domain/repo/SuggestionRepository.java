package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Suggestion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SuggestionRepository {
    private final JdbcClient jdbc;
    private final JsonCodec json;

    public SuggestionRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private RowMapper<Suggestion> mapper() {
        return (rs, i) -> {
            String field = rs.getString("field");
            return new Suggestion(
                    rs.getString("id"),
                    rs.getString("candidate_id"),
                    rs.getString("session_id"),
                    field,
                    json.suggestionValue(field, rs.getString("value_json")),
                    rs.getString("label"),
                    rs.getBigDecimal("confidence").doubleValue(),
                    json.evidence(rs.getString("evidence")),
                    rs.getString("status"),
                    rs.getString("prompt_version"),
                    rs.getString("decided_by"),
                    Rows.instant(rs, "decided_at"));
        };
    }

    private static final String SELECT =
            "select id, candidate_id, session_id, field, value_json, label, confidence, evidence, status, prompt_version, decided_by, decided_at from suggestions";

    public List<Suggestion> findByCandidate(String candidateId) {
        return jdbc.sql(SELECT + " where candidate_id = :c order by confidence desc, label")
                .param("c", candidateId)
                .query(mapper())
                .list();
    }

    public List<Suggestion> findBySession(String sessionId) {
        return jdbc.sql(SELECT + " where session_id = :s order by confidence desc, label")
                .param("s", sessionId)
                .query(mapper())
                .list();
    }

    public List<Suggestion> findByOwner(String ownerMemberId) {
        return jdbc.sql(
                        SELECT
                                + " where candidate_id in (select id from candidates where owner_member_id = :m) order by confidence desc, label")
                .param("m", ownerMemberId)
                .query(mapper())
                .list();
    }

    public Optional<Suggestion> findById(String orgId, String id) {
        return jdbc.sql(SELECT + " where organization_id = :org and id = :id")
                .param("org", orgId)
                .param("id", id)
                .query(mapper())
                .optional();
    }

    public boolean exists(String id) {
        return jdbc.sql("select count(*) from suggestions where id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    public void insert(String orgId, Suggestion s, Instant now) {
        jdbc.sql(
                        """
                insert into suggestions (id, organization_id, candidate_id, session_id, field, value_json, label, confidence, evidence, status, prompt_version, decided_by, decided_at, created_at)
                values (:id, :org, :cand, :session, :field, :value, :label, :conf, :evidence, :status, :pv, :by, :at, :now)
                """)
                .param("id", s.id())
                .param("org", orgId)
                .param("cand", s.candidateId())
                .param("session", s.sessionId())
                .param("field", s.field())
                .param("value", json.toJson(s.field(), s.value()))
                .param("label", s.label())
                .param("conf", BigDecimal.valueOf(s.confidence()))
                .param("evidence", json.write(s.evidence()))
                .param("status", s.status())
                .param("pv", s.promptVersion())
                .param("by", s.decidedBy())
                .param("at", s.decidedAt())
                .param("now", now)
                .update();
    }

    public void updateEvidence(Suggestion s) {
        jdbc.sql(
                        "update suggestions set label = :label, confidence = :conf, evidence = :evidence where id = :id")
                .param("label", s.label())
                .param("conf", BigDecimal.valueOf(s.confidence()))
                .param("evidence", json.write(s.evidence()))
                .param("id", s.id())
                .update();
    }

    public void setStatus(String id, String status, String decidedBy, Instant decidedAt) {
        jdbc.sql(
                        "update suggestions set status = :s, decided_by = :by, decided_at = :at where id = :id")
                .param("s", status)
                .param("by", decidedBy)
                .param("at", decidedAt)
                .param("id", id)
                .update();
    }

    public void attachSession(String candidateId, String sessionId) {
        jdbc.sql("update suggestions set session_id = :s where candidate_id = :c")
                .param("s", sessionId)
                .param("c", candidateId)
                .update();
    }

    public void deleteProposedByCandidate(String candidateId) {
        jdbc.sql("delete from suggestions where candidate_id = :c and status = 'proposed'")
                .param("c", candidateId)
                .update();
    }
}
