package com.cognition.est.domain.service;

import com.cognition.est.ai.SuggestionEngine;
import com.cognition.est.app.web.NotFoundException;
import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Candidate;
import com.cognition.est.domain.model.CatalogItem;
import com.cognition.est.domain.model.Client;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.model.Person;
import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.model.Suggestion;
import com.cognition.est.domain.model.Team;
import com.cognition.est.domain.repo.CandidateRepository;
import com.cognition.est.domain.repo.CatalogRepository;
import com.cognition.est.domain.repo.ClientRepository;
import com.cognition.est.domain.repo.EventRepository;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import com.cognition.est.domain.repo.SessionRepository;
import com.cognition.est.domain.repo.SuggestionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Read models for the API (port of selectors.ts, timeline.ts and today.ts). */
@Service
public class Views {
    static final List<String> KEY_FIELDS =
            List.of(
                    "title",
                    "clientId",
                    "teamId",
                    "sessionType",
                    "facilitatorId",
                    "contacts",
                    "participantIds",
                    "participantsEstimated",
                    "technologyIds",
                    "useCases",
                    "summary");
    static final List<String> COMPLETENESS_FIELDS =
            List.of(
                    "clientId",
                    "teamId",
                    "sessionType",
                    "facilitatorId",
                    "contacts",
                    "participantIds",
                    "useCases",
                    "outcomeId");
    static final Map<String, String> FIELD_LABELS =
            Map.ofEntries(
                    Map.entry("title", "Title"),
                    Map.entry("clientId", "Client"),
                    Map.entry("teamId", "Team"),
                    Map.entry("sessionType", "Format"),
                    Map.entry("facilitatorId", "Facilitator"),
                    Map.entry("coFacilitatorIds", "Co-facilitators"),
                    Map.entry("contacts", "Contacts"),
                    Map.entry("participantIds", "Participants"),
                    Map.entry("participantsEstimated", "Participants"),
                    Map.entry("technologyIds", "Technologies"),
                    Map.entry("useCases", "Use cases"),
                    Map.entry("outcomeId", "Session outcome"),
                    Map.entry("summary", "Summary"),
                    Map.entry("notes", "Notes"),
                    Map.entry("state", "State"));

    private final SessionRepository sessions;
    private final CandidateRepository candidates;
    private final SuggestionRepository suggestions;
    private final EventRepository events;
    private final MemberRepository members;
    private final ClientRepository clients;
    private final PersonRepository people;
    private final CatalogRepository catalog;

    public Views(
            SessionRepository sessions,
            CandidateRepository candidates,
            SuggestionRepository suggestions,
            EventRepository events,
            MemberRepository members,
            ClientRepository clients,
            PersonRepository people,
            CatalogRepository catalog) {
        this.sessions = sessions;
        this.candidates = candidates;
        this.suggestions = suggestions;
        this.events = events;
        this.members = members;
        this.clients = clients;
        this.people = people;
        this.catalog = catalog;
    }

    /* ───────────────────────── records ───────────────────────── */

    public record Missing(String field, String label) {}

    public record ContactView(Person person, String roleId) {}

    public record SessionDetail(
            Session session,
            Client client,
            Team team,
            Member facilitator,
            List<Member> coFacilitators,
            List<ContactView> contacts,
            List<Person> participants,
            List<Suggestion> pending,
            List<CalendarEvent> sourceEvents,
            Candidate candidate,
            List<Missing> missing,
            double completeness,
            List<TimelineItem> history) {}

    public record Coverage(int filled, int total) {}

    public record CandidateCard(
            Candidate candidate,
            CalendarEvent event,
            List<Suggestion> suggestions,
            Coverage coverage,
            String clientLabel,
            String sessionTypeLabel,
            String duplicateOfTitle,
            int recurringSiblings) {}

    public record InboxStats(int newEvents, int likely, int review, double prefillRatio) {}

    public record Inbox(
            List<CandidateCard> likely,
            List<CandidateCard> review,
            List<CandidateCard> duplicates,
            List<CandidateCard> unlikely,
            List<CandidateCard> dismissed,
            InboxStats stats) {}

    public record TimelineItem(
            String kind,
            String id,
            Instant start,
            Instant end,
            String title,
            String clientId,
            String clientLabel,
            String facilitatorId,
            String sessionType,
            String sessionTypeLabel,
            List<String> technologyIds,
            String state,
            String outcomeId,
            int useCaseCount,
            double completeness,
            int pending,
            int participants,
            String bucket,
            String href) {}

    public record TodayBoard(
            LocalDate date, List<TimelineItem> today, List<TimelineItem> overdue) {}

    /* ───────────────────────── shared lookups ───────────────────────── */

    private record Ref(
            Map<String, Client> clients,
            Map<String, Team> teams,
            Map<String, Member> members,
            Map<String, Person> people,
            Map<String, CatalogItem> catalog) {
        String label(String id) {
            if (id == null) return null;
            CatalogItem c = catalog.get(id);
            return c == null ? id : c.label();
        }
    }

    private Ref ref(String orgId) {
        return new Ref(
                index(clients.findAll(orgId), Client::id),
                index(clients.findTeams(orgId), Team::id),
                index(members.findAll(orgId), Member::id),
                index(people.findAll(orgId), Person::id),
                index(catalog.findAll(orgId), CatalogItem::id));
    }

    private static <T> Map<String, T> index(
            List<T> items, java.util.function.Function<T, String> key) {
        Map<String, T> m = new LinkedHashMap<>();
        for (T t : items) m.put(key.apply(t), t);
        return m;
    }

    /* ───────────────────────── session detail ───────────────────────── */

    public SessionDetail sessionDetail(String orgId, String sessionId) {
        Session s =
                sessions.findById(orgId, sessionId)
                        .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        return sessionDetail(orgId, s, ref(orgId));
    }

    SessionDetail sessionDetail(String orgId, Session s, Ref ref) {
        List<Suggestion> pending =
                suggestions.findBySession(s.id()).stream()
                        .filter(x -> "proposed".equals(x.status()))
                        .toList();
        List<Missing> missing =
                COMPLETENESS_FIELDS.stream()
                        .filter(f -> isEmpty(s, f))
                        .map(f -> new Missing(f, FIELD_LABELS.getOrDefault(f, f)))
                        .toList();
        List<TimelineItem> history =
                s.clientId() == null
                        ? List.of()
                        : sessions.findByOrg(orgId).stream()
                                .filter(
                                        h ->
                                                !h.id().equals(s.id())
                                                        && s.clientId().equals(h.clientId())
                                                        && !Set.of("ignored", "cancelled")
                                                                .contains(h.state()))
                                .sorted(Comparator.comparing(Session::scheduledStart).reversed())
                                .limit(8)
                                .map(h -> sessionItem(h, ref, 0))
                                .toList();
        return new SessionDetail(
                s,
                s.clientId() == null ? null : ref.clients().get(s.clientId()),
                s.teamId() == null ? null : ref.teams().get(s.teamId()),
                s.facilitatorId() == null ? null : ref.members().get(s.facilitatorId()),
                s.coFacilitatorIds().stream()
                        .map(ref.members()::get)
                        .filter(Objects::nonNull)
                        .toList(),
                s.contacts().stream()
                        .filter(c -> ref.people().containsKey(c.personId()))
                        .map(c -> new ContactView(ref.people().get(c.personId()), c.roleId()))
                        .toList(),
                s.participantIds().stream()
                        .map(ref.people()::get)
                        .filter(Objects::nonNull)
                        .toList(),
                pending,
                events.findByIds(s.sourceEventIds()),
                candidates.findBySession(s.id()).orElse(null),
                missing,
                completeness(s),
                history);
    }

    static boolean isEmpty(Session s, String field) {
        return switch (field) {
            case "contacts" -> s.contacts().isEmpty();
            case "participantIds" ->
                    s.participantIds().isEmpty() && s.participantsEstimated() == null;
            case "useCases" -> s.useCases().isEmpty();
            case "technologyIds" -> s.technologyIds().isEmpty();
            case "summary" -> s.summary().isBlank();
            case "outcomeId" -> s.outcomeId() == null;
            case "clientId" -> s.clientId() == null;
            case "teamId" -> s.teamId() == null;
            case "sessionType" -> s.sessionType() == null;
            case "facilitatorId" -> s.facilitatorId() == null;
            default -> false;
        };
    }

    public static double completeness(Session s) {
        long missing = COMPLETENESS_FIELDS.stream().filter(f -> isEmpty(s, f)).count();
        return 1 - (double) missing / COMPLETENESS_FIELDS.size();
    }

    /* ───────────────────────── inbox ───────────────────────── */

    public Inbox inbox(String orgId, String memberId) {
        Ref ref = ref(orgId);
        List<Candidate> all = candidates.findByOwner(memberId);
        Map<String, CalendarEvent> evs = index(events.findByOwner(memberId), CalendarEvent::id);
        Map<String, List<Suggestion>> sugs = new LinkedHashMap<>();
        for (Suggestion s : suggestions.findByOwner(memberId))
            sugs.computeIfAbsent(s.candidateId(), k -> new ArrayList<>()).add(s);
        Map<String, Long> recurring = new LinkedHashMap<>();
        evs.values()
                .forEach(
                        e -> {
                            if (e.recurringEventId() != null)
                                recurring.merge(e.recurringEventId(), 1L, Long::sum);
                        });
        Map<String, Candidate> byId = index(all, Candidate::id);

        List<CandidateCard> cards =
                all.stream()
                        .filter(c -> evs.containsKey(c.eventId()))
                        .map(
                                c -> {
                                    CalendarEvent e = evs.get(c.eventId());
                                    List<Suggestion> props =
                                            sugs.getOrDefault(c.id(), List.of()).stream()
                                                    .filter(s -> "proposed".equals(s.status()))
                                                    .toList();
                                    Set<String> filled =
                                            props.stream()
                                                    .filter(
                                                            s ->
                                                                    SuggestionEngine.tierFor(
                                                                                            s
                                                                                                    .confidence())
                                                                                    == SuggestionEngine
                                                                                            .Tier
                                                                                            .PREFILL
                                                                            && KEY_FIELDS.contains(
                                                                                    s.field()))
                                                    .map(Suggestion::field)
                                                    .collect(java.util.stream.Collectors.toSet());
                                    String clientLabel =
                                            props.stream()
                                                    .filter(s -> "clientId".equals(s.field()))
                                                    .max(
                                                            Comparator.comparingDouble(
                                                                    Suggestion::confidence))
                                                    .map(Suggestion::label)
                                                    .orElse(null);
                                    String typeLabel =
                                            props.stream()
                                                    .filter(s -> "sessionType".equals(s.field()))
                                                    .max(
                                                            Comparator.comparingDouble(
                                                                    Suggestion::confidence))
                                                    .map(Suggestion::label)
                                                    .orElse(null);
                                    String dupTitle =
                                            c.duplicateOfCandidateId() == null
                                                    ? null
                                                    : java.util.Optional.ofNullable(
                                                                    byId.get(
                                                                            c
                                                                                    .duplicateOfCandidateId()))
                                                            .map(d -> evs.get(d.eventId()))
                                                            .map(CalendarEvent::title)
                                                            .orElse(null);
                                    int siblings =
                                            e.recurringEventId() == null
                                                    ? 0
                                                    : (int)
                                                            (recurring.getOrDefault(
                                                                            e.recurringEventId(),
                                                                            1L)
                                                                    - 1);
                                    return new CandidateCard(
                                            c,
                                            e,
                                            props,
                                            new Coverage(filled.size(), KEY_FIELDS.size()),
                                            clientLabel,
                                            typeLabel,
                                            dupTitle,
                                            siblings);
                                })
                        .sorted(Comparator.comparing((CandidateCard k) -> k.event().start()))
                        .toList();

        List<CandidateCard> open =
                cards.stream().filter(k -> "open".equals(k.candidate().status())).toList();
        List<CandidateCard> duplicates =
                open.stream().filter(k -> k.candidate().duplicateOfCandidateId() != null).toList();
        List<CandidateCard> likely =
                open.stream()
                        .filter(
                                k ->
                                        k.candidate().duplicateOfCandidateId() == null
                                                && "likely"
                                                        .equals(
                                                                k.candidate()
                                                                        .classification()
                                                                        .bucket()))
                        .toList();
        List<CandidateCard> review =
                open.stream()
                        .filter(
                                k ->
                                        k.candidate().duplicateOfCandidateId() == null
                                                && "review"
                                                        .equals(
                                                                k.candidate()
                                                                        .classification()
                                                                        .bucket()))
                        .toList();
        List<CandidateCard> unlikely =
                open.stream()
                        .filter(
                                k ->
                                        k.candidate().duplicateOfCandidateId() == null
                                                && "unlikely"
                                                        .equals(
                                                                k.candidate()
                                                                        .classification()
                                                                        .bucket()))
                        .toList();
        List<CandidateCard> dismissed =
                cards.stream().filter(k -> "dismissed".equals(k.candidate().status())).toList();
        List<CandidateCard> scored = new ArrayList<>(likely);
        scored.addAll(review);
        double ratio =
                scored.isEmpty()
                        ? 0
                        : scored.stream()
                                .mapToDouble(
                                        k -> (double) k.coverage().filled() / k.coverage().total())
                                .average()
                                .orElse(0);
        return new Inbox(
                likely,
                review,
                duplicates,
                unlikely,
                dismissed,
                new InboxStats(open.size(), likely.size(), review.size(), ratio));
    }

    /* ───────────────────────── timeline / today ───────────────────────── */

    TimelineItem sessionItem(Session s, Ref ref, int pending) {
        Client c = s.clientId() == null ? null : ref.clients().get(s.clientId());
        return new TimelineItem(
                "session",
                s.id(),
                s.scheduledStart(),
                s.scheduledEnd(),
                s.title(),
                s.clientId(),
                c == null ? null : c.name(),
                s.facilitatorId(),
                s.sessionType(),
                ref.label(s.sessionType()),
                s.technologyIds(),
                s.state(),
                s.outcomeId(),
                s.useCases().size(),
                completeness(s),
                pending,
                s.participantIds().isEmpty()
                        ? (s.participantsEstimated() == null ? 0 : s.participantsEstimated())
                        : s.participantIds().size(),
                null,
                "/sessions/" + s.id());
    }

    TimelineItem candidateItem(Candidate c, CalendarEvent e, List<Suggestion> props, Ref ref) {
        var client =
                props.stream()
                        .filter(s -> "clientId".equals(s.field()))
                        .max(Comparator.comparingDouble(Suggestion::confidence));
        String clientId = client.map(s -> s.value() instanceof String id ? id : null).orElse(null);
        String typeId =
                props.stream()
                        .filter(s -> "sessionType".equals(s.field()))
                        .max(Comparator.comparingDouble(Suggestion::confidence))
                        .map(s -> (String) s.value())
                        .orElse(null);
        String facilitator =
                props.stream()
                        .filter(s -> "facilitatorId".equals(s.field()))
                        .max(Comparator.comparingDouble(Suggestion::confidence))
                        .map(s -> (String) s.value())
                        .orElse(c.ownerMemberId());
        List<String> tech =
                props.stream()
                        .filter(
                                s ->
                                        "technologyIds".equals(s.field())
                                                && SuggestionEngine.tierFor(s.confidence())
                                                        != SuggestionEngine.Tier.FOLDED)
                        .map(s -> (String) s.value())
                        .toList();
        long external =
                e.attendees().stream()
                        .filter(a -> !a.email().toLowerCase().endsWith("@cognition.ai"))
                        .count();
        return new TimelineItem(
                "candidate",
                c.id(),
                e.start(),
                e.end(),
                e.title(),
                clientId,
                client.map(Suggestion::label).orElse(null),
                facilitator,
                typeId,
                ref.label(typeId),
                tech,
                "candidate",
                null,
                (int) props.stream().filter(s -> "useCases".equals(s.field())).count(),
                0,
                props.size(),
                (int) external,
                c.classification().bucket(),
                null);
    }

    public record TimelineFilter(
            Instant from,
            Instant to,
            String clientId,
            String facilitatorId,
            String technologyId,
            String state,
            boolean mine,
            boolean includeCandidates) {}

    public List<TimelineItem> timeline(String orgId, String memberId, TimelineFilter f) {
        Ref ref = ref(orgId);
        List<TimelineItem> items = new ArrayList<>();
        Map<String, Integer> pendingBySession = new LinkedHashMap<>();
        for (Candidate c : candidates.findByOrg(orgId)) {
            if (c.sessionId() != null)
                suggestions.findByCandidate(c.id()).stream()
                        .filter(s -> "proposed".equals(s.status()))
                        .forEach(s -> pendingBySession.merge(c.sessionId(), 1, Integer::sum));
        }
        for (Session s : sessions.findByOrg(orgId)) {
            if ("ignored".equals(s.state()) || "cancelled".equals(s.state())) continue;
            if (f.mine() && !isMine(s, memberId)) continue;
            items.add(sessionItem(s, ref, pendingBySession.getOrDefault(s.id(), 0)));
        }
        if (f.includeCandidates()) {
            Map<String, CalendarEvent> evs = index(events.findByOwner(memberId), CalendarEvent::id);
            for (Candidate c : candidates.findByOwner(memberId)) {
                if (!"open".equals(c.status())
                        || "unlikely".equals(c.classification().bucket())
                        || c.duplicateOfCandidateId() != null
                        || !evs.containsKey(c.eventId())) continue;
                items.add(
                        candidateItem(
                                c,
                                evs.get(c.eventId()),
                                suggestions.findByCandidate(c.id()).stream()
                                        .filter(s -> "proposed".equals(s.status()))
                                        .toList(),
                                ref));
            }
        }
        return items.stream()
                .filter(it -> f.from() == null || !it.start().isBefore(f.from()))
                .filter(it -> f.to() == null || !it.start().isAfter(f.to()))
                .filter(it -> f.clientId() == null || f.clientId().equals(it.clientId()))
                .filter(
                        it ->
                                f.facilitatorId() == null
                                        || f.facilitatorId().equals(it.facilitatorId()))
                .filter(
                        it ->
                                f.technologyId() == null
                                        || it.technologyIds().contains(f.technologyId()))
                .filter(it -> f.state() == null || f.state().equals(it.state()))
                .sorted(Comparator.comparing(TimelineItem::start))
                .toList();
    }

    static boolean isMine(Session s, String memberId) {
        return memberId.equals(s.facilitatorId())
                || s.coFacilitatorIds().contains(memberId)
                || memberId.equals(s.createdBy());
    }

    public TodayBoard today(String orgId, String memberId, LocalDate date, ZoneId zone) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<TimelineItem> all =
                timeline(
                        orgId,
                        memberId,
                        new TimelineFilter(null, null, null, null, null, null, true, true));
        List<TimelineItem> today =
                all.stream()
                        .filter(it -> !it.start().isBefore(dayStart) && it.start().isBefore(dayEnd))
                        .toList();
        Instant floor = date.minusDays(14).atStartOfDay(zone).toInstant();
        List<TimelineItem> overdue =
                all.stream()
                        .filter(
                                it ->
                                        "session".equals(it.kind())
                                                && it.start().isBefore(dayStart)
                                                && !it.start().isBefore(floor)
                                                && it.completeness() < 1)
                        .sorted(Comparator.comparing(TimelineItem::start).reversed())
                        .toList();
        return new TodayBoard(date, today, overdue);
    }
}
