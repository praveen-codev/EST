package com.cognition.est.domain.service;

import com.cognition.est.ai.EngineContext;
import com.cognition.est.domain.repo.CatalogRepository;
import com.cognition.est.domain.repo.ClientRepository;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import com.cognition.est.domain.repo.SessionRepository;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds the engine's view of an organisation: reference data plus every non-ignored/cancelled
 * session.
 */
@Component
public class EngineContextFactory {
    private static final Set<String> EXCLUDED = Set.of("ignored", "cancelled");

    private final MemberRepository members;
    private final ClientRepository clients;
    private final PersonRepository people;
    private final CatalogRepository catalog;
    private final SessionRepository sessions;

    public EngineContextFactory(
            MemberRepository members,
            ClientRepository clients,
            PersonRepository people,
            CatalogRepository catalog,
            SessionRepository sessions) {
        this.members = members;
        this.clients = clients;
        this.people = people;
        this.catalog = catalog;
        this.sessions = sessions;
    }

    public EngineContext forMember(String orgId, String ownerMemberId) {
        return new EngineContext(
                members.findAll(orgId),
                clients.findAll(orgId),
                clients.findTeams(orgId),
                people.findAll(orgId),
                catalog.findAll(orgId),
                sessions.findByOrg(orgId).stream()
                        .filter(s -> !EXCLUDED.contains(s.state()))
                        .toList(),
                ownerMemberId);
    }
}
