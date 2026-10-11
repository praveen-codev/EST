package com.cognition.est.auth;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.app.security.Principal;
import com.cognition.est.domain.model.Identity;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.repo.IdentityRepository;
import com.cognition.est.domain.repo.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Auth")
public class AuthController {
    private final GoogleIdTokenVerifier verifier;
    private final AuthService auth;
    private final TokenService tokens;
    private final MemberRepository members;
    private final IdentityRepository identities;
    private final EstProperties props;

    public AuthController(
            GoogleIdTokenVerifier verifier,
            AuthService auth,
            TokenService tokens,
            MemberRepository members,
            IdentityRepository identities,
            EstProperties props) {
        this.verifier = verifier;
        this.auth = auth;
        this.tokens = tokens;
        this.members = members;
        this.identities = identities;
        this.props = props;
    }

    public record GoogleBody(@NotBlank String idToken) {}

    public record TokenResponse(
            String accessToken, String tokenType, Instant expiresAt, Member member) {}

    public record Me(Member member, List<Identity> identities, Map<String, String> organization) {}

    @PostMapping("/auth/google")
    @Operation(summary = "Exchange a Google ID token for an app access token and a refresh cookie")
    public TokenResponse google(
            @Valid @RequestBody GoogleBody body, HttpServletRequest req, HttpServletResponse res) {
        GoogleIdentity g;
        try {
            g = verifier.verify(body.idToken());
        } catch (GoogleIdTokenVerifier.InvalidTokenException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
        Member m;
        try {
            m = auth.resolve(g, Instant.now());
        } catch (AuthService.SignInRejected e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        return issue(m, req, res);
    }

    TokenResponse issue(Member m, HttpServletRequest req, HttpServletResponse res) {
        TokenService.Issued issued = tokens.issue(m, req.getHeader("User-Agent"), Instant.now());
        AuthCookies.set(
                res,
                issued.refreshToken(),
                issued.csrf(),
                issued.refreshExpiresAt(),
                props.jwt().cookieSecure());
        return new TokenResponse(issued.access().token(), "Bearer", issued.access().expiresAt(), m);
    }

    @PostMapping("/auth/refresh")
    @Operation(
            summary =
                    "Rotate the refresh token and mint a new access token (cookie + X-CSRF header)")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest req, HttpServletResponse res) {
        if (!AuthCookies.csrfOk(req)) {
            AuthCookies.clear(res, props.jwt().cookieSecure());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing or mismatched CSRF token");
        }
        Instant now = Instant.now();
        var rotation = tokens.rotate(AuthCookies.cookie(req, AuthCookies.REFRESH), now);
        if (rotation.isEmpty()) {
            AuthCookies.clear(res, props.jwt().cookieSecure());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Refresh token is invalid, expired or revoked");
        }
        var r = rotation.get();
        Member m =
                members.findById(r.memberId())
                        .filter(x -> "ACTIVE".equals(x.status()))
                        .orElse(null);
        if (m == null) {
            tokens.revoke(r.refreshToken(), now);
            AuthCookies.clear(res, props.jwt().cookieSecure());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Member is no longer active");
        }
        AuthCookies.set(
                res, r.refreshToken(), r.csrf(), r.refreshExpiresAt(), props.jwt().cookieSecure());
        TokenService.Access access = tokens.mintAccess(m, now);
        return ResponseEntity.ok(
                new TokenResponse(access.token(), "Bearer", access.expiresAt(), m));
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Revoke the refresh-token family and clear cookies")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res) {
        if (AuthCookies.csrfOk(req))
            tokens.revoke(AuthCookies.cookie(req, AuthCookies.REFRESH), Instant.now());
        AuthCookies.clear(res, props.jwt().cookieSecure());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Tag(name = "Me")
    public Me me() {
        Principal p = Principal.current();
        Member m =
                members.findById(p.memberId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "Unknown member"));
        List<Identity> ids =
                identities.findByMember(m.id()).stream().map(Identity::withoutSyncToken).toList();
        return new Me(m, ids, Map.of("id", m.orgId(), "name", "Cognition Enablement"));
    }
}
