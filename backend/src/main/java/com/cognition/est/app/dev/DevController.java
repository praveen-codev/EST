package com.cognition.est.app.dev;

import com.cognition.est.app.config.EstProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Development conveniences, active only when {@code est.dev.endpoints-enabled=true} (local
 * profile). Phase 0 ships token minting so the API can be exercised from Swagger; the auth track
 * (1B) replaces this with the full mock-Google dev-login flow and refresh cookies.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(prefix = "est.dev", name = "endpoints-enabled", havingValue = "true")
@Tag(name = "Dev", description = "Local-profile only")
public class DevController {

    private final JdbcClient jdbc;
    private final JwtEncoder encoder;
    private final EstProperties props;

    public DevController(JdbcClient jdbc, JwtEncoder encoder, EstProperties props) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.props = props;
    }

    @PostMapping("/token")
    @Operation(summary = "Mint an access token for a seeded member (local only)")
    public Map<String, Object> token(@RequestParam String email) {
        var row =
                jdbc.sql("select id, organization_id, role, status from members where email = :e")
                        .param("e", email.toLowerCase())
                        .query(
                                (rs, i) ->
                                        new Object[] {
                                            rs.getString(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4)
                                        })
                        .optional()
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "No member with that email"));
        if (!"ACTIVE".equals(row[3])) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member is not active");
        }
        Instant now = Instant.now();
        Instant exp = now.plus(props.jwt().accessTokenTtl());
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(props.jwt().issuer())
                        .issuedAt(now)
                        .expiresAt(exp)
                        .subject((String) row[0])
                        .claim("org", row[1])
                        .claim("role", row[2])
                        .claim("email", email.toLowerCase())
                        .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return Map.of("accessToken", token, "tokenType", "Bearer", "expiresAt", exp.toString());
    }
}
