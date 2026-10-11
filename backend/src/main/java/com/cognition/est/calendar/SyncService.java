package com.cognition.est.calendar;

import com.cognition.est.ai.SuggestionEngine;
import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Candidate;
import com.cognition.est.domain.model.Identity;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.model.Suggestion;
import com.cognition.est.domain.model.SyncRun;
import com.cognition.est.domain.repo.CandidateRepository;
import com.cognition.est.domain.repo.EventRepository;
import com.cognition.est.domain.repo.IdentityRepository;
import com.cognition.est.domain.repo.JsonCodec;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import com.cognition.est.domain.repo.SuggestionRepository;
import com.cognition.est.domain.repo.SyncRunRepository;
import com.cognition.est.domain.service.EngineContextFactory;
import com.cognition.est.domain.service.SessionOps;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendar sync (port of runSync in web/lib/domain/ops.ts). For each linked identity: fetch the
 * next page from the provider, upsert evidence by provider id, dedupe against the member's other
 * events, analyse new events into candidates + proposals, re-analyse changed ones (manual fields
 * protected), and record a sync run. Nothing is ever auto-confirmed.
 */
@Service
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final Map<String, CalendarProvider> providers;
    private final IdentityRepository identities;
    private final MemberRepository members;
    private final EventRepository events;
    private final CandidateRepository candidates;
    private final SuggestionRepository suggestions;
    private final PersonRepository people;
    private final SyncRunRepository runs;
    private final EngineContextFactory contexts;
    private final SessionOps ops;
    private final JsonCodec json;

    public SyncService(
            List<CalendarProvider> providerList,
            IdentityRepository identities,
            MemberRepository members,
            EventRepository events,
            CandidateRepository candidates,
            SuggestionRepository suggestions,
            PersonRepository people,
            SyncRunRepository runs,
            EngineContextFactory contexts,
            SessionOps ops,
            JsonCodec json) {
        this.providers =
                providerList.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        CalendarProvider::name, Function.identity()));
        this.identities = identities;
        this.members = members;
        this.events = events;
        this.candidates = candidates;
        this.suggestions = suggestions;
        this.people = people;
        this.runs = runs;
        this.contexts = contexts;
        this.ops = ops;
        this.json = json;
    }

    public boolean hasProvider(String name) {
        return providers.containsKey(name);
    }

    /** Sync every active identity of a member; returns one run per identity. */
    public List<SyncRun> syncMember(String orgId, String memberId, String kind, Instant now) {
        List<SyncRun> out = new ArrayList<>();
        for (Identity idn : identities.findByMember(memberId)) {
            if (!"ACTIVE".equals(idn.status())) continue;
            out.add(syncIdentity(orgId, idn, kind, now));
        }
        return out;
    }

    @Transactional
    public SyncRun syncIdentity(String orgId, Identity idn, String kind, Instant now) {
        CalendarProvider provider = providers.get(idn.provider());
        String runId = Ids.fresh("run");
        String runKind = idn.syncToken() == null ? "initial" : kind;
        SyncRun running =
                new SyncRun(runId, idn.id(), runKind, now, null, "running", 0, 0, 0, 0, 0, null);
        runs.insert(orgId, running);
        if (provider == null) {
            SyncRun failed =
                    finish(
                            running,
                            now,
                            "failed",
                            0,
                            0,
                            0,
                            0,
                            0,
                            "No provider registered for " + idn.provider());
            identities.recordError(idn.id(), failed.error(), false, now);
            return failed;
        }
        Member owner = members.findById(idn.memberId()).orElseThrow();
        try {
            CalendarProvider.Page page =
                    provider.fetch(
                            new CalendarProvider.IdentityRef(
                                    idn.id(),
                                    idn.memberId(),
                                    idn.email(),
                                    idn.provider(),
                                    idn.syncToken()),
                            owner,
                            now);
            if (page.fullResync()) identities.clearSyncToken(idn.id());
            Counters c = ingest(orgId, idn, owner, page.events(), runId, now);
            identities.recordSync(idn.id(), page.nextSyncToken(), now);
            return finish(
                    running,
                    now,
                    "ok",
                    page.events().size(),
                    c.added,
                    c.updated,
                    c.unchanged,
                    c.candidates,
                    null);
        } catch (CalendarProvider.CalendarAccessException e) {
            log.warn(
                    "sync failed for identity {} ({}): {}",
                    idn.id(),
                    idn.provider(),
                    e.getMessage());
            identities.recordError(idn.id(), e.getMessage(), e.needsReauth, now);
            return finish(running, now, "failed", 0, 0, 0, 0, 0, e.getMessage());
        }
    }

    private static final class Counters {
        int added, updated, unchanged, candidates;
    }

    private Counters ingest(
            String orgId,
            Identity idn,
            Member owner,
            List<ProviderEvent> incoming,
            String runId,
            Instant now) {
        Counters c = new Counters();
        for (ProviderEvent p : incoming) {
            CalendarEvent next = EventFingerprint.toCalendarEvent(p, idn.id(), owner.id());
            var existing = events.findById(next.id());
            if (existing.isPresent()) {
                if (Objects.equals(existing.get().etag(), next.etag())) {
                    c.unchanged++;
                    continue;
                }
                c.updated++;
                events.upsert(orgId, next, p.raw(), runId, now);
                candidates
                        .findByEvent(next.id())
                        .ifPresent(cand -> ops.reanalyzeCandidate(orgId, cand, next, now));
                continue;
            }
            c.added++;
            events.upsert(orgId, next, p.raw(), runId, now);
            String candidateId =
                    EventFingerprint.candidateIdFor(owner.id(), next.providerEventId());
            SuggestionEngine.Result result =
                    SuggestionEngine.analyze(
                            next, candidateId, contexts.forMember(orgId, owner.id()), json);
            List<CalendarEvent> ownerEvents = events.findByOwner(owner.id());
            CalendarEvent dup =
                    next.cancelled() ? null : EventFingerprint.findDuplicate(next, ownerEvents);
            String dupCandidate =
                    dup == null
                            ? null
                            : candidates
                                    .findByEvent(dup.id())
                                    .filter(x -> !"dismissed".equals(x.status()))
                                    .map(Candidate::id)
                                    .orElse(null);
            people.insertMissing(orgId, result.newPeople(), now);
            candidates.upsert(
                    orgId,
                    new Candidate(
                            candidateId,
                            next.id(),
                            owner.id(),
                            result.classification(),
                            dupCandidate,
                            "open",
                            null,
                            result.redactedText(),
                            result.redactions(),
                            now,
                            now));
            for (Suggestion s : result.suggestions())
                if (!suggestions.exists(s.id())) suggestions.insert(orgId, s, now);
            c.candidates++;
        }
        return c;
    }

    private SyncRun finish(
            SyncRun r,
            Instant now,
            String status,
            int seen,
            int added,
            int updated,
            int unchanged,
            int created,
            String error) {
        SyncRun done =
                new SyncRun(
                        r.id(),
                        r.identityId(),
                        r.kind(),
                        r.startedAt(),
                        now,
                        status,
                        seen,
                        added,
                        updated,
                        unchanged,
                        created,
                        error);
        runs.finish(done);
        return done;
    }
}
