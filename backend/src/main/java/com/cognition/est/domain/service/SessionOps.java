package com.cognition.est.domain.service;

import com.cognition.est.ai.Extractor;
import com.cognition.est.ai.SuggestionEngine;
import com.cognition.est.app.web.ConflictException;
import com.cognition.est.app.web.NotFoundException;
import com.cognition.est.audit.AuditRepository;
import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Candidate;
import com.cognition.est.domain.model.Client;
import com.cognition.est.domain.model.ClientCreate;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.model.SessionContact;
import com.cognition.est.domain.model.Suggestion;
import com.cognition.est.domain.model.UseCase;
import com.cognition.est.domain.repo.CandidateRepository;
import com.cognition.est.domain.repo.ClientRepository;
import com.cognition.est.domain.repo.EventRepository;
import com.cognition.est.domain.repo.JsonCodec;
import com.cognition.est.domain.repo.PersonRepository;
import com.cognition.est.domain.repo.SessionRepository;
import com.cognition.est.domain.repo.SuggestionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the state transitions in web/lib/domain/ops.ts: confirming/dismissing/merging candidates,
 * deciding suggestions, manual edits with provenance, use cases, and re-running the engine. Rules
 * that never bend: AI writes only to the proposal layer; a field a human set manually is never
 * overwritten; every decision is recorded with who/when.
 */
@Service
public class SessionOps {
    private final EventRepository events;
    private final CandidateRepository candidates;
    private final SuggestionRepository suggestions;
    private final SessionRepository sessions;
    private final ClientRepository clients;
    private final PersonRepository people;
    private final EngineContextFactory contexts;
    private final JsonCodec json;
    private final AuditRepository audit;

    public SessionOps(
            EventRepository events,
            CandidateRepository candidates,
            SuggestionRepository suggestions,
            SessionRepository sessions,
            ClientRepository clients,
            PersonRepository people,
            EngineContextFactory contexts,
            JsonCodec json,
            AuditRepository audit) {
        this.audit = audit;
        this.events = events;
        this.candidates = candidates;
        this.suggestions = suggestions;
        this.sessions = sessions;
        this.clients = clients;
        this.people = people;
        this.contexts = contexts;
        this.json = json;
    }

    public static String sessionIdForEvent(String eventId) {
        return Ids.stableId("ses", eventId);
    }

    /* ───────────────────────── candidates ───────────────────────── */

    @Transactional
    public Session confirmCandidate(
            String orgId, String candidateId, String memberId, Instant now) {
        Candidate cand =
                candidates
                        .findById(orgId, candidateId)
                        .orElseThrow(() -> new NotFoundException("No candidate " + candidateId));
        if (!"open".equals(cand.status())) {
            if (cand.sessionId() != null)
                return sessions.findById(orgId, cand.sessionId()).orElseThrow();
            throw new ConflictException("Candidate is " + cand.status());
        }
        CalendarEvent event =
                events.findById(cand.eventId())
                        .orElseThrow(() -> new NotFoundException("Event missing for candidate"));
        Session.Builder b = blankSession(event, memberId, now).toBuilder();
        Set<String> decided = new HashSet<>();
        for (Suggestion s : suggestions.findByCandidate(candidateId)) {
            if (!"proposed".equals(s.status())
                    || SuggestionEngine.tierFor(s.confidence()) != SuggestionEngine.Tier.PREFILL)
                continue;
            applyToSession(orgId, b, s, "prefilled", memberId, now);
            decided.add(s.id());
        }
        Session session = b.build();
        sessions.insert(orgId, session);
        suggestions.attachSession(candidateId, session.id());
        for (String id : decided) suggestions.setStatus(id, "accepted", memberId, now);
        candidates.setStatus(candidateId, "confirmed", session.id(), now);
        audit.record(
                orgId,
                memberId,
                "candidate.confirmed",
                "session",
                session.id(),
                Map.of("prefilled", decided.size()));
        return session;
    }

    @Transactional
    public void dismissCandidate(String orgId, String candidateId, Instant now) {
        Candidate c =
                candidates
                        .findById(orgId, candidateId)
                        .orElseThrow(() -> new NotFoundException("No candidate " + candidateId));
        if ("open".equals(c.status())) candidates.setStatus(candidateId, "dismissed", null, now);
    }

    @Transactional
    public void restoreCandidate(String orgId, String candidateId, Instant now) {
        Candidate c =
                candidates
                        .findById(orgId, candidateId)
                        .orElseThrow(() -> new NotFoundException("No candidate " + candidateId));
        if ("dismissed".equals(c.status()) || "merged".equals(c.status()))
            candidates.setStatus(candidateId, "open", null, now);
    }

    /**
     * Treats the duplicate's event as another source for the original's session (confirming the
     * original first if needed).
     */
    @Transactional
    public Session mergeDuplicate(String orgId, String candidateId, String memberId, Instant now) {
        Candidate cand =
                candidates
                        .findById(orgId, candidateId)
                        .orElseThrow(() -> new NotFoundException("No candidate " + candidateId));
        if (cand.duplicateOfCandidateId() == null)
            throw new IllegalArgumentException("Candidate is not flagged as a duplicate");
        Candidate original =
                candidates
                        .findById(orgId, cand.duplicateOfCandidateId())
                        .orElseThrow(() -> new NotFoundException("Original candidate missing"));
        Session session =
                original.sessionId() != null
                        ? sessions.findById(orgId, original.sessionId()).orElseThrow()
                        : confirmCandidate(orgId, original.id(), memberId, now);
        if (!session.sourceEventIds().contains(cand.eventId())) {
            Session.Builder b = session.toBuilder();
            b.sourceEventIds.add(cand.eventId());
            save(orgId, session, b);
            session = b.build();
        }
        candidates.setStatus(candidateId, "merged", null, now);
        return session;
    }

    @Transactional
    public List<String> confirmAllLikely(String orgId, String memberId, Instant now) {
        List<String> ids = new ArrayList<>();
        for (Candidate c : candidates.findByOwner(memberId)) {
            if (!"open".equals(c.status())
                    || !"likely".equals(c.classification().bucket())
                    || c.duplicateOfCandidateId() != null) continue;
            ids.add(confirmCandidate(orgId, c.id(), memberId, now).id());
        }
        return ids;
    }

    /* ───────────────────────── suggestion decisions ───────────────────────── */

    @Transactional
    public Session decideSuggestion(
            String orgId, String suggestionId, boolean accept, String memberId, Instant now) {
        Suggestion s =
                suggestions
                        .findById(orgId, suggestionId)
                        .orElseThrow(() -> new NotFoundException("No suggestion " + suggestionId));
        if (s.sessionId() == null)
            throw new ConflictException("Suggestion belongs to an unconfirmed candidate");
        Session session =
                sessions.findById(orgId, s.sessionId())
                        .orElseThrow(() -> new NotFoundException("Session missing"));
        if (!"proposed".equals(s.status())) return session;
        if (accept) {
            Session.Builder b = session.toBuilder();
            applyToSession(orgId, b, s, "accepted", memberId, now);
            save(orgId, session, b);
            session = b.build();
            if (!Extractor.ITEM_FIELDS.contains(s.field())) {
                for (Suggestion other : suggestions.findBySession(session.id())) {
                    if (!other.id().equals(s.id())
                            && other.field().equals(s.field())
                            && "proposed".equals(other.status()))
                        suggestions.setStatus(other.id(), "superseded", null, null);
                }
            }
        }
        suggestions.setStatus(s.id(), accept ? "accepted" : "rejected", memberId, now);
        audit.record(
                orgId,
                memberId,
                accept ? "suggestion.accepted" : "suggestion.rejected",
                "suggestion",
                s.id(),
                Map.of("field", s.field(), "sessionId", session.id()));
        return session;
    }

    @Transactional
    public Session acceptAll(
            String orgId, String sessionId, String memberId, double minConfidence, Instant now) {
        Session session =
                sessions.findById(orgId, sessionId)
                        .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        List<Suggestion> pending =
                suggestions.findBySession(sessionId).stream()
                        .filter(
                                s ->
                                        "proposed".equals(s.status())
                                                && s.confidence() >= minConfidence)
                        .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
                        .toList();
        for (Suggestion s : pending) {
            if (suggestions
                    .findById(orgId, s.id())
                    .map(x -> "proposed".equals(x.status()))
                    .orElse(false)) session = decideSuggestion(orgId, s.id(), true, memberId, now);
        }
        return session;
    }

    /* ───────────────────────── manual edits ───────────────────────── */

    /**
     * Applies a manual patch: every touched field becomes provenance=manual and supersedes pending
     * single-value suggestions.
     */
    @Transactional
    public Session updateSession(
            String orgId,
            String sessionId,
            Map<String, Consumer<Session.Builder>> patch,
            Integer expectedVersion,
            String memberId,
            Instant now) {
        Session session =
                sessions.findById(orgId, sessionId)
                        .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        if (expectedVersion != null && expectedVersion != session.version())
            throw new ConflictException(
                    "Session changed since you loaded it (version " + session.version() + ").");
        Session.Builder b = session.toBuilder();
        for (var e : patch.entrySet()) {
            e.getValue().accept(b);
            b.provenance.put(e.getKey(), "manual");
        }
        b.updatedBy = memberId;
        b.updatedAt = now;
        save(orgId, session, b);
        for (Suggestion s : suggestions.findBySession(sessionId)) {
            if ("proposed".equals(s.status())
                    && patch.containsKey(s.field())
                    && !Extractor.ITEM_FIELDS.contains(s.field()))
                suggestions.setStatus(s.id(), "superseded", null, null);
        }
        return b.build();
    }

    @Transactional
    public Session addUseCase(
            String orgId, String sessionId, UseCase draft, String memberId, Instant now) {
        Session session =
                sessions.findById(orgId, sessionId)
                        .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        UseCase uc =
                new UseCase(
                        draft != null && draft.id() != null
                                ? draft.id()
                                : Ids.stableId(
                                        "uc",
                                        sessionId + "/" + session.useCases().size() + "/" + now),
                        draft == null || draft.title() == null ? "" : draft.title(),
                        draft == null || draft.description() == null ? "" : draft.description(),
                        draft == null ? null : draft.useCaseTypeId(),
                        draft == null || draft.technologyIds() == null
                                ? List.of()
                                : List.copyOf(draft.technologyIds()));
        return updateSession(
                orgId, sessionId, Map.of("useCases", b -> b.useCases.add(uc)), null, memberId, now);
    }

    @Transactional
    public Session updateUseCase(
            String orgId,
            String sessionId,
            String useCaseId,
            UseCase patch,
            String memberId,
            Instant now) {
        Session session =
                sessions.findById(orgId, sessionId)
                        .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        if (session.useCases().stream().noneMatch(u -> u.id().equals(useCaseId)))
            throw new NotFoundException("No use case " + useCaseId);
        // Ordered: the use case is rewritten first, then technologies roll up from all use cases.
        Map<String, Consumer<Session.Builder>> mutations = new java.util.LinkedHashMap<>();
        mutations.put(
                "useCases",
                b ->
                        b.useCases.replaceAll(
                                u ->
                                        !u.id().equals(useCaseId)
                                                ? u
                                                : new UseCase(
                                                        u.id(),
                                                        patch.title() == null
                                                                ? u.title()
                                                                : patch.title(),
                                                        patch.description() == null
                                                                ? u.description()
                                                                : patch.description(),
                                                        patch.useCaseTypeId() == null
                                                                ? u.useCaseTypeId()
                                                                : patch.useCaseTypeId(),
                                                        patch.technologyIds() == null
                                                                ? u.technologyIds()
                                                                : List.copyOf(
                                                                        patch.technologyIds()))));
        mutations.put(
                "technologyIds",
                b -> {
                    Set<String> all = new LinkedHashSet<>(b.technologyIds);
                    b.useCases.forEach(u -> all.addAll(u.technologyIds()));
                    b.technologyIds = new ArrayList<>(all);
                });
        return updateSession(orgId, sessionId, mutations, null, memberId, now);
    }

    @Transactional
    public Session removeUseCase(
            String orgId, String sessionId, String useCaseId, String memberId, Instant now) {
        return updateSession(
                orgId,
                sessionId,
                Map.of("useCases", b -> b.useCases.removeIf(u -> u.id().equals(useCaseId))),
                null,
                memberId,
                now);
    }

    /* ───────────────────────── re-analysis ───────────────────────── */

    /** Re-run the engine for a confirmed session using today's history; never overwrites. */
    @Transactional
    public Session rerunAI(String orgId, String sessionId, Instant now) {
        Session session =
                sessions.findById(orgId, sessionId)
                        .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        Candidate cand = candidates.findBySession(sessionId).orElse(null);
        if (cand == null) return session;
        CalendarEvent event = events.findById(cand.eventId()).orElse(null);
        if (event == null) return session;
        reanalyzeCandidate(orgId, cand, event, now);
        return session;
    }

    /**
     * Evidence changed: old proposals are superseded, new ones added; a confirmed session only
     * receives chips for fields a human hasn't set manually and whose value actually differs.
     */
    @Transactional
    public void reanalyzeCandidate(String orgId, Candidate cand, CalendarEvent event, Instant now) {
        SuggestionEngine.Result result =
                SuggestionEngine.analyze(
                        event, cand.id(), contexts.forMember(orgId, cand.ownerMemberId()), json);
        Optional<Session> session =
                cand.sessionId() == null
                        ? Optional.empty()
                        : sessions.findById(orgId, cand.sessionId());
        List<Suggestion> previous = suggestions.findByCandidate(cand.id());
        Set<String> previousIds =
                previous.stream().map(Suggestion::id).collect(java.util.stream.Collectors.toSet());
        Set<String> freshIds =
                result.suggestions().stream()
                        .map(Suggestion::id)
                        .collect(java.util.stream.Collectors.toSet());
        for (Suggestion s : previous) {
            if ("proposed".equals(s.status()) && !freshIds.contains(s.id()))
                suggestions.setStatus(s.id(), "superseded", null, null);
        }
        people.insertMissing(orgId, result.newPeople(), now);
        for (Suggestion s : result.suggestions()) {
            if (previousIds.contains(s.id())) continue;
            if (session.isPresent()) {
                if ("manual".equals(session.get().provenance().get(s.field()))) continue;
                if (sessionAlreadyHas(session.get(), s)) continue;
            }
            suggestions.insert(orgId, s.withSessionId(session.map(Session::id).orElse(null)), now);
        }
        candidates.upsert(
                orgId,
                new Candidate(
                        cand.id(),
                        cand.eventId(),
                        cand.ownerMemberId(),
                        result.classification(),
                        cand.duplicateOfCandidateId(),
                        cand.status(),
                        cand.sessionId(),
                        result.redactedText(),
                        result.redactions(),
                        cand.createdAt(),
                        now));
    }

    boolean sessionAlreadyHas(Session session, Suggestion s) {
        return switch (s.field()) {
            case "technologyIds" -> session.technologyIds().contains((String) s.value());
            case "useCases" -> {
                UseCase uc = (UseCase) s.value();
                yield session.useCases().stream()
                        .anyMatch(
                                u ->
                                        u.id().equals(uc.id())
                                                || u.title().equalsIgnoreCase(uc.title()));
            }
            case "coFacilitatorIds" -> session.coFacilitatorIds().contains((String) s.value());
            case "contacts" -> {
                SessionContact v = (SessionContact) s.value();
                yield session.contacts().stream()
                        .anyMatch(
                                c ->
                                        c.personId().equals(v.personId())
                                                && c.roleId().equals(v.roleId()));
            }
            case "participantIds" -> {
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) s.value();
                yield session.participantIds().stream()
                        .sorted()
                        .toList()
                        .equals(ids.stream().sorted().toList());
            }
            case "clientId" -> s.value() instanceof String id && id.equals(session.clientId());
            case "title" -> Objects.equals(session.title(), s.value());
            case "summary" -> Objects.equals(session.summary(), s.value());
            case "teamId" -> Objects.equals(session.teamId(), s.value());
            case "facilitatorId" -> Objects.equals(session.facilitatorId(), s.value());
            case "sessionType" -> Objects.equals(session.sessionType(), s.value());
            case "participantsEstimated" ->
                    Objects.equals(session.participantsEstimated(), s.value());
            default -> false;
        };
    }

    /* ───────────────────────── internals ───────────────────────── */

    private Session blankSession(CalendarEvent event, String memberId, Instant now) {
        return new Session(
                sessionIdForEvent(event.id()),
                event.end().isBefore(now) ? "completed" : "planned",
                event.title(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                event.start(),
                event.end(),
                null,
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                "",
                "",
                List.of(event.id()),
                Map.of(),
                memberId,
                now,
                memberId,
                now,
                1);
    }

    private String materializeClient(String orgId, Object value, String memberId, Instant now) {
        if (value instanceof String id) return id;
        if (!(value instanceof ClientCreate create)) return null;
        String id = Ids.stableId("cli", create.name());
        if (clients.findById(orgId, id).isEmpty()) {
            clients.insert(
                    orgId,
                    new Client(
                            id,
                            create.name(),
                            null,
                            List.of(create.domain()),
                            (create.name().length() * 47) % 360),
                    memberId,
                    now);
            List<String> orphans =
                    people.findAll(orgId).stream()
                            .filter(
                                    p ->
                                            p.clientId() == null
                                                    && p.email() != null
                                                    && p.email()
                                                            .toLowerCase()
                                                            .endsWith("@" + create.domain()))
                            .map(p -> p.id())
                            .toList();
            people.attachClient(orphans, id, now);
        }
        return id;
    }

    @SuppressWarnings("unchecked")
    private void applyToSession(
            String orgId,
            Session.Builder b,
            Suggestion s,
            String provenance,
            String memberId,
            Instant now) {
        switch (s.field()) {
            case "clientId" -> b.clientId = materializeClient(orgId, s.value(), memberId, now);
            case "technologyIds" -> {
                if (!b.technologyIds.contains((String) s.value()))
                    b.technologyIds.add((String) s.value());
            }
            case "useCases" -> {
                UseCase uc = (UseCase) s.value();
                if (b.useCases.stream().noneMatch(u -> u.id().equals(uc.id()))) {
                    b.useCases.add(
                            new UseCase(
                                    uc.id(),
                                    uc.title(),
                                    uc.description(),
                                    uc.useCaseTypeId(),
                                    List.copyOf(uc.technologyIds())));
                    Set<String> all = new LinkedHashSet<>(b.technologyIds);
                    all.addAll(uc.technologyIds());
                    b.technologyIds = new ArrayList<>(all);
                }
            }
            case "coFacilitatorIds" -> {
                if (!b.coFacilitatorIds.contains((String) s.value()))
                    b.coFacilitatorIds.add((String) s.value());
            }
            case "contacts" -> {
                SessionContact v = (SessionContact) s.value();
                if (b.contacts.stream()
                        .noneMatch(
                                c ->
                                        c.personId().equals(v.personId())
                                                && c.roleId().equals(v.roleId())))
                    b.contacts.add(v);
            }
            case "participantIds" -> b.participantIds = new ArrayList<>((List<String>) s.value());
            case "participantsEstimated" -> b.participantsEstimated = (Integer) s.value();
            case "title" -> b.title = (String) s.value();
            case "summary" -> b.summary = (String) s.value();
            case "teamId" -> b.teamId = (String) s.value();
            case "facilitatorId" -> b.facilitatorId = (String) s.value();
            case "sessionType" -> b.sessionType = (String) s.value();
            default -> {}
        }
        if (!"manual".equals(b.provenance.get(s.field()))) b.provenance.put(s.field(), provenance);
    }

    /** Persist a mutated builder as the next version of {@code before}. */
    private void save(String orgId, Session before, Session.Builder b) {
        b.version = before.version() + 1;
        if (!sessions.update(orgId, b.build(), before.version()))
            throw new ConflictException("Session changed concurrently; retry.");
    }
}
