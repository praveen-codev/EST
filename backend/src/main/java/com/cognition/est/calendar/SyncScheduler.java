package com.cognition.est.calendar;

import com.cognition.est.domain.model.Identity;
import com.cognition.est.domain.repo.IdentityRepository;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic incremental sync of every active identity. Single-instance guard via an in-process flag;
 * when the API runs on more than one node, front this with a DB lock (ShedLock) — tracked in the
 * hardening phase.
 */
@Component
@ConditionalOnProperty(
        prefix = "est.sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);
    private final IdentityRepository identities;
    private final SyncService sync;
    private final AtomicBoolean running = new AtomicBoolean();

    public SyncScheduler(IdentityRepository identities, SyncService sync) {
        this.identities = identities;
        this.sync = sync;
    }

    @Scheduled(
            fixedDelayString = "${est.sync.interval:PT15M}",
            initialDelayString = "${est.sync.initial-delay:PT2M}")
    public void tick() {
        if (!running.compareAndSet(false, true)) return;
        try {
            int ok = 0, failed = 0;
            for (Identity idn : identities.findActive("org_cognition")) {
                var run = sync.syncIdentity("org_cognition", idn, "incremental", Instant.now());
                if ("ok".equals(run.status())) ok++;
                else failed++;
            }
            if (ok + failed > 0) log.info("scheduled sync: {} ok, {} failed", ok, failed);
        } finally {
            running.set(false);
        }
    }
}
