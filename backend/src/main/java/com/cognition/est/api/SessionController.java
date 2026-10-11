package com.cognition.est.api;

import com.cognition.est.ai.SuggestionEngine;
import com.cognition.est.app.security.Principal;
import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.model.SessionContact;
import com.cognition.est.domain.model.UseCase;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.service.SessionOps;
import com.cognition.est.domain.service.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Sessions")
public class SessionController {
    private static final Set<String> EDITABLE =
            Set.of(
                    "title",
                    "state",
                    "clientId",
                    "teamId",
                    "facilitatorId",
                    "coFacilitatorIds",
                    "contacts",
                    "participantIds",
                    "participantsEstimated",
                    "sessionType",
                    "outcomeId",
                    "technologyIds",
                    "summary",
                    "notes");
    private static final Set<String> STATES =
            Set.of("planned", "completed", "incomplete", "cancelled", "ignored");

    private final SessionOps ops;
    private final Views views;
    private final MemberRepository members;
    private final JsonMapper mapper = JsonMapper.shared();

    public SessionController(SessionOps ops, Views views, MemberRepository members) {
        this.ops = ops;
        this.views = views;
        this.members = members;
    }

    @GetMapping("/today")
    @Operation(
            summary =
                    "The caller's sessions and open candidates scheduled today, plus overdue feedback")
    public Views.TodayBoard today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        Principal p = Principal.current();
        ZoneId zone =
                members.findById(p.memberId())
                        .map(m -> ZoneId.of(m.timeZone()))
                        .orElse(ZoneId.of("UTC"));
        return views.today(
                p.orgId(), p.memberId(), date == null ? LocalDate.now(zone) : date, zone);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Timeline items (sessions and optionally open candidates) with filters")
    public List<Views.TimelineItem> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String facilitatorId,
            @RequestParam(required = false) String technologyId,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "everyone") String scope,
            @RequestParam(defaultValue = "true") boolean includeCandidates) {
        Principal p = Principal.current();
        return views.timeline(
                p.orgId(),
                p.memberId(),
                new Views.TimelineFilter(
                        from,
                        to,
                        clientId,
                        facilitatorId,
                        technologyId,
                        state,
                        "mine".equals(scope),
                        includeCandidates));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<Views.SessionDetail> get(@PathVariable String id) {
        Views.SessionDetail d = views.sessionDetail(Principal.current().orgId(), id);
        return ResponseEntity.ok().eTag(Versions.etag(d.session().version())).body(d);
    }

    @PatchMapping("/sessions/{id}")
    @Operation(
            summary =
                    "Manual edit: each present field becomes provenance=manual and supersedes pending single-value suggestions")
    public ResponseEntity<Views.SessionDetail> patch(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody Map<String, Object> body) {
        Principal p = Principal.current();
        Map<String, Consumer<Session.Builder>> patch = new LinkedHashMap<>();
        for (var e : body.entrySet()) {
            if (!EDITABLE.contains(e.getKey()))
                throw new IllegalArgumentException("Field is not editable: " + e.getKey());
            patch.put(e.getKey(), mutation(e.getKey(), mapper.valueToTree(e.getValue())));
        }
        Session s =
                ops.updateSession(
                        p.orgId(), id, patch, Versions.parse(ifMatch), p.memberId(), Instant.now());
        return ResponseEntity.ok()
                .eTag(Versions.etag(s.version()))
                .body(views.sessionDetail(p.orgId(), id));
    }

    private Consumer<Session.Builder> mutation(String field, JsonNode v) {
        return switch (field) {
            case "title" -> b -> b.title = v.asString();
            case "state" ->
                    b -> {
                        if (!STATES.contains(v.asString()))
                            throw new IllegalArgumentException("Unknown state");
                        b.state = v.asString();
                    };
            case "clientId" -> b -> b.clientId = nullable(v);
            case "teamId" -> b -> b.teamId = nullable(v);
            case "facilitatorId" -> b -> b.facilitatorId = nullable(v);
            case "sessionType" -> b -> b.sessionType = nullable(v);
            case "outcomeId" -> b -> b.outcomeId = nullable(v);
            case "summary" -> b -> b.summary = v.isNull() ? "" : v.asString();
            case "notes" -> b -> b.notes = v.isNull() ? "" : v.asString();
            case "participantsEstimated" ->
                    b -> b.participantsEstimated = v.isNull() ? null : v.asInt();
            case "coFacilitatorIds" -> b -> b.coFacilitatorIds = strings(v);
            case "participantIds" -> b -> b.participantIds = strings(v);
            case "technologyIds" -> b -> b.technologyIds = strings(v);
            case "contacts" ->
                    b -> {
                        List<SessionContact> out = new ArrayList<>();
                        v.forEach(
                                c ->
                                        out.add(
                                                new SessionContact(
                                                        c.get("personId").asString(),
                                                        c.get("roleId").asString())));
                        b.contacts = out;
                    };
            default -> throw new IllegalArgumentException("Field is not editable: " + field);
        };
    }

    private static String nullable(JsonNode v) {
        return v == null || v.isNull() || v.asString().isBlank() ? null : v.asString();
    }

    private static List<String> strings(JsonNode v) {
        List<String> out = new ArrayList<>();
        if (v != null && v.isArray()) v.forEach(x -> out.add(x.asString()));
        return out;
    }

    public record UseCaseBody(
            String title, String description, String useCaseTypeId, List<String> technologyIds) {
        UseCase toUseCase(String id) {
            return new UseCase(id, title, description, useCaseTypeId, technologyIds);
        }
    }

    @PostMapping("/sessions/{id}/use-cases")
    public ResponseEntity<Views.SessionDetail> addUseCase(
            @PathVariable String id, @RequestBody(required = false) UseCaseBody body) {
        Principal p = Principal.current();
        ops.addUseCase(
                p.orgId(),
                id,
                body == null ? null : body.toUseCase(null),
                p.memberId(),
                Instant.now());
        return ResponseEntity.status(201).body(views.sessionDetail(p.orgId(), id));
    }

    @PatchMapping("/sessions/{id}/use-cases/{useCaseId}")
    public Views.SessionDetail updateUseCase(
            @PathVariable String id,
            @PathVariable String useCaseId,
            @RequestBody UseCaseBody body) {
        Principal p = Principal.current();
        ops.updateUseCase(
                p.orgId(), id, useCaseId, body.toUseCase(useCaseId), p.memberId(), Instant.now());
        return views.sessionDetail(p.orgId(), id);
    }

    @DeleteMapping("/sessions/{id}/use-cases/{useCaseId}")
    public Views.SessionDetail removeUseCase(
            @PathVariable String id, @PathVariable String useCaseId) {
        Principal p = Principal.current();
        ops.removeUseCase(p.orgId(), id, useCaseId, p.memberId(), Instant.now());
        return views.sessionDetail(p.orgId(), id);
    }

    @PostMapping("/sessions/{id}/suggestions/accept-all")
    @Tag(name = "Suggestions")
    public Views.SessionDetail acceptAll(@PathVariable String id) {
        Principal p = Principal.current();
        ops.acceptAll(p.orgId(), id, p.memberId(), SuggestionEngine.CHIP_THRESHOLD, Instant.now());
        return views.sessionDetail(p.orgId(), id);
    }

    @PostMapping("/sessions/{id}/rerun-ai")
    @Tag(name = "Suggestions")
    public Views.SessionDetail rerun(@PathVariable String id) {
        Principal p = Principal.current();
        ops.rerunAI(p.orgId(), id, Instant.now());
        return views.sessionDetail(p.orgId(), id);
    }
}
