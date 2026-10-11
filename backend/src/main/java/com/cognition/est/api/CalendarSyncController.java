package com.cognition.est.api;

import com.cognition.est.app.security.Principal;
import com.cognition.est.calendar.SyncService;
import com.cognition.est.domain.model.SyncRun;
import com.cognition.est.domain.repo.SyncRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "Calendar")
public class CalendarSyncController {
    private final SyncService sync;
    private final SyncRunRepository runs;

    public CalendarSyncController(SyncService sync, SyncRunRepository runs) {
        this.sync = sync;
        this.runs = runs;
    }

    @PostMapping("/sync")
    @Operation(summary = "Run an incremental sync now for all of the caller's linked calendars")
    public List<SyncRun> syncNow() {
        Principal p = Principal.current();
        return sync.syncMember(p.orgId(), p.memberId(), "manual", Instant.now());
    }

    @GetMapping("/sync-runs")
    public List<SyncRun> runs(@RequestParam(defaultValue = "20") int limit) {
        return runs.findRecentForMember(
                Principal.current().memberId(), Math.min(100, Math.max(1, limit)));
    }
}
