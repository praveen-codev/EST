package com.cognition.est.app.dev;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.auth.AuthCookies;
import com.cognition.est.auth.TokenService;
import com.cognition.est.calendar.MockCalendarProvider;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.repo.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Development conveniences, active only when {@code est.dev.endpoints-enabled=true} (local
 * profile): sign in as a seeded persona without Google, reset the demo data, pick a mock calendar
 * scenario. Never enabled in qa/prod.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(prefix = "est.dev", name = "endpoints-enabled", havingValue = "true")
@Tag(name = "Dev", description = "Local-profile only")
public class DevController {
    private final JdbcClient jdbc;
    private final MemberRepository members;
    private final TokenService tokens;
    private final EstProperties props;
    private final MockCalendarProvider mock;
    private final LocalSeedRunner seed;

    public DevController(
            JdbcClient jdbc,
            MemberRepository members,
            TokenService tokens,
            EstProperties props,
            MockCalendarProvider mock,
            LocalSeedRunner seed) {
        this.jdbc = jdbc;
        this.members = members;
        this.tokens = tokens;
        this.props = props;
        this.mock = mock;
        this.seed = seed;
    }

    private Member active(String email) {
        Member m =
                members.findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "No member with that email"));
        if (!"ACTIVE".equals(m.status()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member is not active");
        return m;
    }

    @PostMapping("/token")
    @Operation(summary = "Mint a bare access token for a seeded member (tests, curl)")
    public Map<String, Object> token(@RequestParam String email) {
        Member m = active(email);
        TokenService.Access a = tokens.mintAccess(m, Instant.now());
        return Map.of(
                "accessToken",
                a.token(),
                "tokenType",
                "Bearer",
                "expiresAt",
                a.expiresAt().toString(),
                "member",
                m);
    }

    @PostMapping("/login-as")
    @Operation(
            summary =
                    "Mock Google sign-in as a seeded persona: access token + refresh cookie, like /auth/google")
    public Map<String, Object> loginAs(
            @RequestParam String email, HttpServletRequest req, HttpServletResponse res) {
        Member m = active(email);
        TokenService.Issued issued = tokens.issue(m, req.getHeader("User-Agent"), Instant.now());
        AuthCookies.set(
                res,
                issued.refreshToken(),
                issued.csrf(),
                issued.refreshExpiresAt(),
                props.jwt().cookieSecure());
        return Map.of(
                "accessToken",
                issued.access().token(),
                "tokenType",
                "Bearer",
                "expiresAt",
                issued.access().expiresAt().toString(),
                "member",
                m);
    }

    @PostMapping("/scenario/{scenario}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary =
                    "Select the mock calendar fixture set (clean | messy | empty) and reset demo data")
    public void scenario(@PathVariable String scenario) {
        if (!Set.of("clean", "messy", "empty").contains(scenario))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown scenario");
        mock.setScenario(scenario);
        reset();
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Wipe events, candidates, suggestions and non-seed sessions, then re-seed")
    @Transactional
    public void reset() {
        for (String sql :
                new String[] {
                    "delete from suggestions",
                    "delete from candidates",
                    "delete from calendar_event_attendees",
                    "delete from calendar_events",
                    "delete from calendar_sync_runs",
                    "delete from session_source_events",
                    "delete from use_case_technologies",
                    "delete from use_cases",
                    "delete from session_co_facilitators",
                    "delete from session_contacts",
                    "delete from session_participants",
                    "delete from session_technologies",
                    "delete from sessions",
                    "delete from refresh_token_families",
                    "delete from oauth_link_states",
                    "delete from member_identities",
                    "delete from people",
                    "delete from client_domains",
                    "delete from teams",
                    "delete from clients",
                    "delete from catalog_items where updated_by is not null",
                    "delete from audit_events",
                    "delete from members",
                    "delete from local_seed_marker"
                }) {
            jdbc.sql(sql).update();
        }
        seed.run(null);
    }
}
