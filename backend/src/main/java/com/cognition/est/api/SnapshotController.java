package com.cognition.est.api;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.app.security.Principal;
import com.cognition.est.calendar.MockCalendarProvider;
import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Candidate;
import com.cognition.est.domain.model.CatalogItem;
import com.cognition.est.domain.model.Client;
import com.cognition.est.domain.model.Identity;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.model.Person;
import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.model.Suggestion;
import com.cognition.est.domain.model.SyncRun;
import com.cognition.est.domain.model.Team;
import com.cognition.est.domain.repo.CandidateRepository;
import com.cognition.est.domain.repo.CatalogRepository;
import com.cognition.est.domain.repo.ClientRepository;
import com.cognition.est.domain.repo.EventRepository;
import com.cognition.est.domain.repo.IdentityRepository;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import com.cognition.est.domain.repo.SessionRepository;
import com.cognition.est.domain.repo.SuggestionRepository;
import com.cognition.est.domain.repo.SyncRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One read for the whole client-side model: the caller's slice of events, candidates, suggestions
 * and sync runs, plus org-wide reference data and sessions. The web app keeps its pure selectors
 * (today board, inbox grouping, timeline) and recomputes them from this snapshot after each write.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Me")
public class SnapshotController {
    private final MemberRepository members;
    private final ClientRepository clients;
    private final PersonRepository people;
    private final CatalogRepository catalog;
    private final SessionRepository sessions;
    private final CandidateRepository candidates;
    private final SuggestionRepository suggestions;
    private final EventRepository events;
    private final SyncRunRepository runs;
    private final IdentityRepository identities;
    private final EstProperties props;
    private final ObjectProvider<MockCalendarProvider> mock;

    public SnapshotController(
            MemberRepository members,
            ClientRepository clients,
            PersonRepository people,
            CatalogRepository catalog,
            SessionRepository sessions,
            CandidateRepository candidates,
            SuggestionRepository suggestions,
            EventRepository events,
            SyncRunRepository runs,
            IdentityRepository identities,
            EstProperties props,
            ObjectProvider<MockCalendarProvider> mock) {
        this.members = members;
        this.clients = clients;
        this.people = people;
        this.catalog = catalog;
        this.sessions = sessions;
        this.candidates = candidates;
        this.suggestions = suggestions;
        this.events = events;
        this.runs = runs;
        this.identities = identities;
        this.props = props;
        this.mock = mock;
    }

    public record Features(
            boolean devMode, boolean mockGoogle, String scenario, int remainingPages) {}

    public record Snapshot(
            Member me,
            Features features,
            List<Member> members,
            List<Client> clients,
            List<Team> teams,
            List<Person> people,
            List<CatalogItem> catalog,
            List<Session> sessions,
            List<Candidate> candidates,
            List<Suggestion> suggestions,
            List<CalendarEvent> events,
            List<SyncRun> syncRuns,
            List<Identity> identities,
            Map<String, String> organization) {}

    @GetMapping("/snapshot")
    @Operation(summary = "The caller's full client-side model in one read")
    public Snapshot snapshot() {
        Principal p = Principal.current();
        Member me = members.findById(p.memberId()).orElseThrow();
        MockCalendarProvider m = mock.getIfAvailable();
        int remaining = 0;
        if (m != null) {
            Identity primary =
                    identities.findByMember(me.id()).stream()
                            .filter(i -> "mock".equals(i.provider()) && "ACTIVE".equals(i.status()))
                            .findFirst()
                            .orElse(null);
            int cursor =
                    primary == null || primary.syncToken() == null
                            ? 0
                            : Integer.parseInt(primary.syncToken());
            remaining = Math.max(0, m.remainingPages(me.email()) - cursor);
        }
        Features features =
                new Features(
                        props.dev().endpointsEnabled(),
                        props.google().mock(),
                        m == null ? null : m.scenario(),
                        remaining);
        return new Snapshot(
                me,
                features,
                members.findAll(p.orgId()),
                clients.findAll(p.orgId()),
                clients.findTeams(p.orgId()),
                people.findAll(p.orgId()),
                catalog.findAll(p.orgId()),
                sessions.findByOrg(p.orgId()),
                candidates.findByOwner(me.id()),
                suggestions.findByOwner(me.id()),
                events.findByOwner(me.id()),
                runs.findRecentForMember(me.id(), 50),
                identities.findByMember(me.id()).stream().map(Identity::withoutSyncToken).toList(),
                Map.of("id", p.orgId(), "name", "Cognition Enablement"));
    }
}
