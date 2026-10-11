package com.cognition.est.ai;

import com.cognition.est.domain.model.CatalogItem;
import com.cognition.est.domain.model.Client;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.model.Person;
import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.model.Team;
import java.util.List;

/**
 * Everything the engine may look at: the org's reference data and (non-ignored) session history.
 */
public record EngineContext(
        List<Member> members,
        List<Client> clients,
        List<Team> teams,
        List<Person> people,
        List<CatalogItem> catalog,
        List<Session> sessions,
        String ownerMemberId) {

    public List<CatalogItem> catalogOf(String kind) {
        return catalog.stream()
                .filter(c -> c.kind().equals(kind) && !c.archived())
                .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
                .toList();
    }
}
