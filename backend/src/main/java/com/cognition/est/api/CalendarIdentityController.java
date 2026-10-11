package com.cognition.est.api;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.app.security.Principal;
import com.cognition.est.calendar.CalendarProvider;
import com.cognition.est.calendar.IdentityService;
import com.cognition.est.calendar.SyncService;
import com.cognition.est.domain.model.Identity;
import com.cognition.est.domain.repo.IdentityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar/identities")
@Tag(name = "Calendar")
public class CalendarIdentityController {
    private static final Logger log = LoggerFactory.getLogger(CalendarIdentityController.class);

    private final IdentityService service;
    private final IdentityRepository identities;
    private final SyncService sync;
    private final EstProperties props;

    public CalendarIdentityController(
            IdentityService service,
            IdentityRepository identities,
            SyncService sync,
            EstProperties props) {
        this.service = service;
        this.identities = identities;
        this.sync = sync;
        this.props = props;
    }

    @GetMapping
    @Operation(summary = "Google accounts linked by the caller (no token material)")
    public List<Identity> list() {
        return service.list(Principal.current().memberId());
    }

    @PostMapping("/link/start")
    @Operation(
            summary =
                    "Begin linking another Google account (authorization code + PKCE, offline access)")
    public Map<String, String> start() {
        return Map.of(
                "authorizationUrl",
                service.startLink(Principal.current().memberId(), Instant.now()));
    }

    @GetMapping("/link/callback")
    @Operation(
            summary =
                    "Google redirects here; the browser is sent back to the web app's settings page")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        String web = props.webOrigins().getFirst() + "/settings/calendars";
        if (error != null) return redirect(web + "?error=" + enc(error));
        try {
            IdentityService.LinkResult r = service.completeLink(code, state, Instant.now());
            // First import right away so the user sees events without waiting for the scheduler.
            identities
                    .findById(r.identityId())
                    .ifPresent(
                            idn ->
                                    sync.syncIdentity(
                                            "org_cognition", idn, "initial", Instant.now()));
            return redirect(web + "?linked=" + enc(r.identityId()));
        } catch (IllegalArgumentException | CalendarProvider.CalendarAccessException e) {
            log.info("calendar link failed: {}", e.getMessage());
            return redirect(web + "?error=" + enc(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unlink: revoke at Google, wipe the token, keep imported evidence")
    public void unlink(@PathVariable String id) {
        service.unlink(Principal.current().memberId(), id, Instant.now());
    }

    private static ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
