package com.cognition.est.calendar;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.app.crypto.TokenCrypto;
import com.cognition.est.app.web.NotFoundException;
import com.cognition.est.audit.AuditRepository;
import com.cognition.est.domain.model.Identity;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.repo.IdentityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linking additional Google accounts ("partner accounts") to a member: authorization code + PKCE,
 * state bound to the member and expiring in 10 minutes, refresh token encrypted at rest. Unlinking
 * revokes at Google and disables the identity but keeps imported evidence.
 */
@Service
public class IdentityService {
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final GoogleOAuthClient oauth;
    private final IdentityRepository identities;
    private final TokenCrypto crypto;
    private final JdbcClient jdbc;
    private final AuditRepository audit;
    private final EstProperties props;
    private final SecureRandom random = new SecureRandom();

    public IdentityService(
            GoogleOAuthClient oauth,
            IdentityRepository identities,
            TokenCrypto crypto,
            JdbcClient jdbc,
            AuditRepository audit,
            EstProperties props) {
        this.oauth = oauth;
        this.identities = identities;
        this.crypto = crypto;
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
    }

    public List<Identity> list(String memberId) {
        return identities.findByMember(memberId).stream().map(Identity::withoutSyncToken).toList();
    }

    @Transactional
    public String startLink(String memberId, Instant now) {
        String state = random();
        String verifier = random() + random();
        String challenge =
                Base64.getUrlEncoder().withoutPadding().encodeToString(sha256Bytes(verifier));
        TokenCrypto.Encrypted enc = crypto.encrypt(verifier);
        jdbc.sql("delete from oauth_link_states where expires_at < :now")
                .param("now", now)
                .update();
        jdbc.sql(
                        "insert into oauth_link_states (state_hash, member_id, pkce_verifier_enc, pkce_verifier_iv, created_at, expires_at) values (:h, :m, :enc, :iv, :now, :exp)")
                .param("h", sha256Hex(state))
                .param("m", memberId)
                .param("enc", enc.ciphertext())
                .param("iv", enc.iv())
                .param("now", now)
                .param("exp", now.plus(STATE_TTL))
                .update();
        return oauth.authorizationUrl(state, challenge);
    }

    public record LinkResult(String identityId, String memberId) {}

    /**
     * Called from the public callback: validates state, exchanges the code, stores the identity.
     */
    @Transactional
    public LinkResult completeLink(String code, String state, Instant now)
            throws CalendarProvider.CalendarAccessException {
        if (state == null || code == null)
            throw new IllegalArgumentException("Missing code or state");
        var row =
                jdbc.sql(
                                "select member_id, pkce_verifier_enc, pkce_verifier_iv, expires_at from oauth_link_states where state_hash = :h")
                        .param("h", sha256Hex(state))
                        .query(
                                (rs, i) ->
                                        new Object[] {
                                            rs.getString(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getTimestamp(4).toInstant()
                                        })
                        .optional()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unknown or already used state"));
        jdbc.sql("delete from oauth_link_states where state_hash = :h")
                .param("h", sha256Hex(state))
                .update();
        if (((Instant) row[3]).isBefore(now))
            throw new IllegalArgumentException("Link request expired; start again");
        String memberId = (String) row[0];
        String verifier =
                crypto.decrypt(new TokenCrypto.Encrypted((String) row[1], (String) row[2]));
        GoogleOAuthClient.Tokens tokens = oauth.exchangeCode(code, verifier);
        if (tokens.refreshToken() == null)
            throw new CalendarProvider.CalendarAccessException(
                    "Google did not return a refresh token (consent not granted offline access)",
                    false,
                    null);
        String provider = props.google().mock() ? "mock" : "google";
        TokenCrypto.Encrypted enc = crypto.encrypt(tokens.refreshToken());
        var existing = identities.findByMemberAndEmail(memberId, provider, tokens.email());
        String id;
        if (existing.isPresent()) {
            id = existing.get().id();
            identities.updateToken(
                    id,
                    new IdentityRepository.EncryptedToken(enc.ciphertext(), enc.iv()),
                    tokens.sub(),
                    tokens.displayName(),
                    now);
        } else {
            id = Ids.fresh("idn");
            identities.insert(
                    "org_cognition",
                    new Identity(
                            id,
                            memberId,
                            provider,
                            tokens.sub(),
                            tokens.email(),
                            tokens.displayName(),
                            tokens.scopes(),
                            "ACTIVE",
                            null,
                            null,
                            null),
                    new IdentityRepository.EncryptedToken(enc.ciphertext(), enc.iv()),
                    now);
        }
        audit.record(
                "org_cognition",
                memberId,
                "identity.linked",
                "identity",
                id,
                Map.of("email", tokens.email(), "provider", provider));
        return new LinkResult(id, memberId);
    }

    @Transactional
    public void unlink(String memberId, String identityId, Instant now) {
        Identity idn =
                identities
                        .findById(identityId)
                        .filter(i -> i.memberId().equals(memberId))
                        .orElseThrow(
                                () -> new NotFoundException("No linked calendar " + identityId));
        identities
                .refreshToken(identityId)
                .ifPresent(
                        enc -> {
                            try {
                                oauth.revoke(
                                        crypto.decrypt(
                                                new TokenCrypto.Encrypted(
                                                        enc.ciphertext(), enc.iv())));
                            } catch (RuntimeException ignored) {
                                // revocation is best effort; the token is wiped below regardless
                            }
                        });
        jdbc.sql(
                        "update member_identities set refresh_token_enc = null, refresh_token_iv = null, sync_token = null, status = 'DISABLED', updated_at = :now where id = :id")
                .param("now", now)
                .param("id", identityId)
                .update();
        audit.record(
                "org_cognition",
                memberId,
                "identity.unlinked",
                "identity",
                identityId,
                Map.of("email", idn.email()));
    }

    private String random() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] sha256Bytes(String s) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(String s) {
        return HexFormat.of().formatHex(sha256Bytes(s));
    }
}
