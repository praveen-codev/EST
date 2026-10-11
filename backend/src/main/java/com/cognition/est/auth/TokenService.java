package com.cognition.est.auth;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.model.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access tokens (RS256, short-lived, stateless) and refresh-token families (opaque, rotating,
 * stored as hashes). Reuse of an already-rotated refresh token is treated as theft: the whole
 * family is revoked.
 */
@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final EstProperties props;
    private final RefreshTokenRepository families;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder encoder, EstProperties props, RefreshTokenRepository families) {
        this.encoder = encoder;
        this.props = props;
        this.families = families;
    }

    public record Access(String token, Instant expiresAt) {}

    public record Issued(
            Access access, String refreshToken, String csrf, Instant refreshExpiresAt) {}

    public Access mintAccess(Member m, Instant now) {
        Instant exp = now.plus(props.jwt().accessTokenTtl());
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(props.jwt().issuer())
                        .issuedAt(now)
                        .expiresAt(exp)
                        .subject(m.id())
                        .claim("org", m.orgId())
                        .claim("role", m.role())
                        .claim("email", m.email())
                        .id(Ids.fresh("jti"))
                        .build();
        String token =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                        .getTokenValue();
        return new Access(token, exp);
    }

    /** Sign-in: new family. */
    @Transactional
    public Issued issue(Member m, String userAgent, Instant now) {
        String refresh = randomToken();
        Instant exp = now.plus(props.jwt().refreshTokenTtl());
        families.create(
                Ids.fresh("rtf"),
                m.id(),
                hash(refresh),
                userAgent == null ? null : hash(userAgent),
                now,
                exp);
        return new Issued(mintAccess(m, now), refresh, randomToken(), exp);
    }

    public record Rotation(
            String memberId, String refreshToken, String csrf, Instant refreshExpiresAt) {}

    /** Refresh: rotate within the family; reuse of the previous token revokes everything. */
    @Transactional
    public Optional<Rotation> rotate(String presented, Instant now) {
        if (presented == null || presented.isBlank()) return Optional.empty();
        String h = hash(presented);
        var fam = families.findByHash(h);
        if (fam.isEmpty()) return Optional.empty();
        var f = fam.get();
        if (f.revokedAt() != null || f.expiresAt().isBefore(now)) return Optional.empty();
        if (h.equals(f.previousHash())) {
            families.revoke(f.id(), true, now);
            return Optional.empty();
        }
        String next = randomToken();
        families.rotate(f.id(), hash(next), now);
        return Optional.of(new Rotation(f.memberId(), next, randomToken(), f.expiresAt()));
    }

    @Transactional
    public void revoke(String presented, Instant now) {
        if (presented == null || presented.isBlank()) return;
        families.findByHash(hash(presented)).ifPresent(f -> families.revoke(f.id(), false, now));
    }

    @Transactional
    public void revokeAll(String memberId, Instant now) {
        families.revokeAllForMember(memberId, now);
    }

    private String randomToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
