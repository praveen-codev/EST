package com.cognition.est.audit;

import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.repo.JsonCodec;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append-only audit trail. Details never contain credentials or token material. */
@Repository
public class AuditRepository {
    private final JdbcClient jdbc;
    private final JsonCodec json;

    public AuditRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(
            String orgId,
            String actorMemberId,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> details) {
        jdbc.sql(
                        "insert into audit_events (id, organization_id, actor_member_id, action, target_type, target_id, details, occurred_at) values (:id, :org, :actor, :action, :type, :target, :details, :now)")
                .param("id", Ids.fresh("aud"))
                .param("org", orgId)
                .param("actor", actorMemberId)
                .param("action", action)
                .param("type", targetType)
                .param("target", targetId)
                .param("details", json.write(details == null ? Map.of() : details))
                .param("now", Instant.now())
                .update();
    }
}
