package com.cognition.est.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Token families: only SHA-256 hashes are stored; the previous hash lets us detect reuse of a
 * rotated token.
 */
@Repository
public class RefreshTokenRepository {
    private final JdbcClient jdbc;

    public RefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Family(
            String id,
            String memberId,
            String currentHash,
            String previousHash,
            int rotationCount,
            Instant expiresAt,
            Instant revokedAt,
            Instant reuseDetectedAt) {}

    public void create(
            String id,
            String memberId,
            String hash,
            String userAgentHash,
            Instant now,
            Instant expiresAt) {
        jdbc.sql(
                        "insert into refresh_token_families (id, member_id, current_hash, previous_hash, rotation_count, user_agent_hash, created_at, last_used_at, expires_at) values (:id, :m, :h, null, 0, :ua, :now, :now, :exp)")
                .param("id", id)
                .param("m", memberId)
                .param("h", hash)
                .param("ua", userAgentHash)
                .param("now", now)
                .param("exp", expiresAt)
                .update();
    }

    public Optional<Family> findByHash(String hash) {
        return jdbc.sql(
                        "select id, member_id, current_hash, previous_hash, rotation_count, expires_at, revoked_at, reuse_detected_at from refresh_token_families where current_hash = :h or previous_hash = :h")
                .param("h", hash)
                .query(
                        (rs, i) ->
                                new Family(
                                        rs.getString(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getString(4),
                                        rs.getInt(5),
                                        rs.getTimestamp(6).toInstant(),
                                        rs.getTimestamp(7) == null
                                                ? null
                                                : rs.getTimestamp(7).toInstant(),
                                        rs.getTimestamp(8) == null
                                                ? null
                                                : rs.getTimestamp(8).toInstant()))
                .optional();
    }

    public void rotate(String id, String newHash, Instant now) {
        jdbc.sql(
                        "update refresh_token_families set previous_hash = current_hash, current_hash = :h, rotation_count = rotation_count + 1, last_used_at = :now where id = :id")
                .param("h", newHash)
                .param("now", now)
                .param("id", id)
                .update();
    }

    public void revoke(String id, boolean reuse, Instant now) {
        jdbc.sql(
                        "update refresh_token_families set revoked_at = coalesce(revoked_at, :now), reuse_detected_at = case when :reuse then :now else reuse_detected_at end where id = :id")
                .param("now", now)
                .param("reuse", reuse)
                .param("id", id)
                .update();
    }

    public int revokeAllForMember(String memberId, Instant now) {
        return jdbc.sql(
                        "update refresh_token_families set revoked_at = :now where member_id = :m and revoked_at is null")
                .param("now", now)
                .param("m", memberId)
                .update();
    }

    public int purgeExpired(Instant now) {
        return jdbc.sql(
                        "delete from refresh_token_families where expires_at < :now or revoked_at < :cut")
                .param("now", now)
                .param("cut", now.minusSeconds(30L * 24 * 3600))
                .update();
    }
}
