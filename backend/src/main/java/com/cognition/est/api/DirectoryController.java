package com.cognition.est.api;

import com.cognition.est.app.security.Principal;
import com.cognition.est.app.web.NotFoundException;
import com.cognition.est.domain.model.Client;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.model.Person;
import com.cognition.est.domain.model.Team;
import com.cognition.est.domain.repo.ClientRepository;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clients, teams, people and members — the directory the feedback form picks from (and adds to via
 * "Other…").
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Clients")
public class DirectoryController {
    private final ClientRepository clients;
    private final PersonRepository people;
    private final MemberRepository members;

    public DirectoryController(
            ClientRepository clients, PersonRepository people, MemberRepository members) {
        this.clients = clients;
        this.people = people;
        this.members = members;
    }

    public record ClientBody(
            @NotBlank @Size(max = 200) String name, String industry, List<String> domains) {}

    public record TeamBody(@NotBlank @Size(max = 200) String name, List<String> keywords) {}

    public record PersonBody(
            @NotBlank @Size(max = 200) String name, String email, String clientId, String roleId) {}

    @GetMapping("/clients")
    public List<Client> clients() {
        return clients.findAll(Principal.current().orgId());
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    public Client createClient(@Valid @RequestBody ClientBody body) {
        Principal p = Principal.current();
        String name = body.name().trim();
        var existing =
                clients.findAll(p.orgId()).stream()
                        .filter(c -> c.name().equalsIgnoreCase(name))
                        .findFirst();
        if (existing.isPresent()) return existing.get();
        Client c =
                new Client(
                        Ids.stableId("cli", name),
                        name,
                        body.industry(),
                        body.domains() == null
                                ? List.of()
                                : body.domains().stream()
                                        .map(d -> d.toLowerCase(Locale.ROOT))
                                        .toList(),
                        (name.length() * 53) % 360);
        clients.insert(p.orgId(), c, p.memberId(), Instant.now());
        return c;
    }

    @GetMapping("/clients/{id}/teams")
    public List<Team> teams(@PathVariable String id) {
        return clients.findTeams(Principal.current().orgId()).stream()
                .filter(t -> t.clientId().equals(id))
                .toList();
    }

    @GetMapping("/teams")
    public List<Team> allTeams() {
        return clients.findTeams(Principal.current().orgId());
    }

    @PostMapping("/clients/{id}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public Team createTeam(@PathVariable String id, @Valid @RequestBody TeamBody body) {
        Principal p = Principal.current();
        clients.findById(p.orgId(), id).orElseThrow(() -> new NotFoundException("No client " + id));
        String name = body.name().trim();
        var existing =
                clients.findTeams(p.orgId()).stream()
                        .filter(t -> t.clientId().equals(id) && t.name().equalsIgnoreCase(name))
                        .findFirst();
        if (existing.isPresent()) return existing.get();
        Team t =
                new Team(
                        Ids.fresh("team"),
                        id,
                        name,
                        body.keywords() == null
                                ? List.of(name.toLowerCase(Locale.ROOT))
                                : body.keywords());
        clients.insertTeam(p.orgId(), t, Instant.now());
        return t;
    }

    @GetMapping("/people")
    @Tag(name = "People")
    public List<Person> people(
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String q) {
        return people.findAll(Principal.current().orgId()).stream()
                .filter(x -> clientId == null || clientId.equals(x.clientId()))
                .filter(
                        x ->
                                q == null
                                        || x.name()
                                                .toLowerCase(Locale.ROOT)
                                                .contains(q.toLowerCase(Locale.ROOT)))
                .toList();
    }

    @PostMapping("/people")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "People")
    public Person createPerson(@Valid @RequestBody PersonBody body) {
        Principal p = Principal.current();
        Person person =
                new Person(
                        Ids.fresh("per"),
                        body.clientId(),
                        body.name().trim(),
                        body.email(),
                        body.roleId());
        people.insert(p.orgId(), person, Instant.now());
        return person;
    }

    @GetMapping("/members")
    @Tag(name = "Me")
    public List<Member> members() {
        return members.findAll(Principal.current().orgId());
    }
}
