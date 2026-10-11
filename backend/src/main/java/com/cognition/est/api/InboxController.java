package com.cognition.est.api;

import com.cognition.est.app.security.Principal;
import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.service.SessionOps;
import com.cognition.est.domain.service.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inbox")
public class InboxController {
    private final SessionOps ops;
    private final Views views;

    public InboxController(SessionOps ops, Views views) {
        this.ops = ops;
        this.views = views;
    }

    @GetMapping("/inbox")
    @Operation(summary = "Open candidates for the caller, grouped, with prefill coverage")
    public Views.Inbox inbox() {
        Principal p = Principal.current();
        return views.inbox(p.orgId(), p.memberId());
    }

    @PostMapping("/candidates/{id}/confirm")
    @Operation(
            summary =
                    "Create the session; prefill-tier suggestions become accepted decisions in one action")
    public Views.SessionDetail confirm(@PathVariable String id) {
        Principal p = Principal.current();
        Session s = ops.confirmCandidate(p.orgId(), id, p.memberId(), Instant.now());
        return views.sessionDetail(p.orgId(), s.id());
    }

    @PostMapping("/candidates/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable String id) {
        ops.dismissCandidate(Principal.current().orgId(), id, Instant.now());
    }

    @PostMapping("/candidates/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable String id) {
        ops.restoreCandidate(Principal.current().orgId(), id, Instant.now());
    }

    @PostMapping("/candidates/{id}/merge")
    public Views.SessionDetail merge(@PathVariable String id) {
        Principal p = Principal.current();
        Session s = ops.mergeDuplicate(p.orgId(), id, p.memberId(), Instant.now());
        return views.sessionDetail(p.orgId(), s.id());
    }

    @PostMapping("/candidates/confirm-all-likely")
    public Map<String, Object> confirmAllLikely() {
        Principal p = Principal.current();
        List<String> ids = ops.confirmAllLikely(p.orgId(), p.memberId(), Instant.now());
        return Map.of("confirmed", ids.size(), "sessionIds", ids);
    }

    @PostMapping("/suggestions/{id}/accept")
    @Tag(name = "Suggestions")
    public Views.SessionDetail accept(@PathVariable String id) {
        Principal p = Principal.current();
        Session s = ops.decideSuggestion(p.orgId(), id, true, p.memberId(), Instant.now());
        return views.sessionDetail(p.orgId(), s.id());
    }

    @PostMapping("/suggestions/{id}/reject")
    @Tag(name = "Suggestions")
    public Views.SessionDetail reject(@PathVariable String id) {
        Principal p = Principal.current();
        Session s = ops.decideSuggestion(p.orgId(), id, false, p.memberId(), Instant.now());
        return views.sessionDetail(p.orgId(), s.id());
    }
}
