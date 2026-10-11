package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Identity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<Identity> MAPPER =
            (rs, i) ->
                    new Identity(
                            rs.getString("id"),
                            rs.getString("member_id"),
                            rs.getString("provider"),
                            rs.getString("provider_sub"),
                            rs.getString("email"),
                            rs.getString("display_name"),
                            Rows.pipeList(rs.getString("scopes")),
                            rs.getString("status"),
                            Rows.instant(rs, "last_sync_at"),
                            rs.getString("last_error"),
                            rs.getString("sync_token"));

    private static final String SELECT =
            "select id, member_id, provider, provider_sub, email, display_name, scopes, status, last_sync_at, last_error, sync_token from member_identities";

    public List<Identity> findByMember(String memberId) {
        return jdbc.sql(SELECT + " where member_id = :m order by created_at")
                .param("m", memberId)
                .query(MAPPER)
                .list();
    }

    public List<Identity> findActive(String orgId) {
        return jdbc.sql(
                        SELECT
                                + " where organization_id = :org and status = 'ACTIVE' order by created_at")
                .param("org", orgId)
                .query(MAPPER)
                .list();
    }

    public Optional<Identity> findById(String id) {
        return jdbc.sql(SELECT + " where id = :id").param("id", id).query(MAPPER).optional();
    }

    public Optional<Identity> findByMemberAndEmail(String memberId, String provider, String email) {
        return jdbc.sql(SELECT + " where member_id = :m and provider = :p and lower(email) = :e")
                .param("m", memberId)
                .param("p", provider)
                .param("e", email.toLowerCase())
                .query(MAPPER)
                .optional();
    }

    public record EncryptedToken(String ciphertext, String iv) {}

    public Optional<EncryptedToken> refreshToken(String id) {
        return jdbc.sql(
                        "select refresh_token_enc, refresh_token_iv from member_identities where id = :id")
                .param("id", id)
                .query(
                        (rs, i) ->
                                rs.getString(1) == null
                                        ? null
                                        : new EncryptedToken(rs.getString(1), rs.getString(2)))
                .optional();
    }

    public void insert(String orgId, Identity idn, EncryptedToken token, Instant now) {
        jdbc.sql(
                        """
                insert into member_identities (id, organization_id, member_id, provider, provider_sub, email, display_name, scopes, refresh_token_enc, refresh_token_iv, sync_token, status, created_at, updated_at)
                values (:id, :org, :m, :p, :sub, :email, :name, :scopes, :enc, :iv, null, :status, :now, :now)
                """)
                .param("id", idn.id())
                .param("org", orgId)
                .param("m", idn.memberId())
                .param("p", idn.provider())
                .param("sub", idn.providerSub())
                .param("email", idn.email().toLowerCase())
                .param("name", idn.displayName())
                .param("scopes", Rows.pipeJoin(idn.scopes()))
                .param("enc", token == null ? null : token.ciphertext())
                .param("iv", token == null ? null : token.iv())
                .param("status", idn.status())
                .param("now", now)
                .update();
    }

    public void updateToken(
            String id, EncryptedToken token, String providerSub, String displayName, Instant now) {
        jdbc.sql(
                        "update member_identities set refresh_token_enc = :enc, refresh_token_iv = :iv, provider_sub = coalesce(:sub, provider_sub), display_name = coalesce(:name, display_name), status = 'ACTIVE', last_error = null, updated_at = :now where id = :id")
                .param("enc", token.ciphertext())
                .param("iv", token.iv())
                .param("sub", providerSub)
                .param("name", displayName)
                .param("now", now)
                .param("id", id)
                .update();
    }

    public void recordSync(String id, String syncToken, Instant now) {
        jdbc.sql(
                        "update member_identities set sync_token = :t, last_sync_at = :now, last_error = null, status = 'ACTIVE', updated_at = :now where id = :id")
                .param("t", syncToken)
                .param("now", now)
                .param("id", id)
                .update();
    }

    public void recordError(String id, String error, boolean needsReauth, Instant now) {
        jdbc.sql(
                        "update member_identities set last_error = :e, status = :s, updated_at = :now where id = :id")
                .param("e", error)
                .param("s", needsReauth ? "NEEDS_REAUTH" : "ACTIVE")
                .param("now", now)
                .param("id", id)
                .update();
    }

    public void clearSyncToken(String id) {
        jdbc.sql("update member_identities set sync_token = null where id = :id")
                .param("id", id)
                .update();
    }

    public void delete(String id) {
        jdbc.sql("delete from member_identities where id = :id").param("id", id).update();
    }
}
