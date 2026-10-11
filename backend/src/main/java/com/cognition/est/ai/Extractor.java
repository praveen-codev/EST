package com.cognition.est.ai;

import com.cognition.est.ai.NormalizedEvent.NormalizedAttendee;
import com.cognition.est.domain.model.CatalogItem;
import com.cognition.est.domain.model.Client;
import com.cognition.est.domain.model.ClientCreate;
import com.cognition.est.domain.model.Evidence;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.model.Person;
import com.cognition.est.domain.model.Session;
import com.cognition.est.domain.model.SessionContact;
import com.cognition.est.domain.model.UseCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Port of web/lib/ai/extract.ts: turns a normalised event plus the org's history into field-level
 * proposals with confidence and evidence. Deterministic; string literals mirror the TS source so
 * the golden fixtures match exactly.
 */
public final class Extractor {
    private Extractor() {}

    public static final String PROMPT_VERSION = "sim-ai/1.0.0";

    /** Fields whose suggestions are individual items appended to a list on accept. */
    public static final Set<String> ITEM_FIELDS =
            Set.of("coFacilitatorIds", "contacts", "technologyIds", "useCases");

    /**
     * A proposal before it gets an id/candidate; evidence is mutable so corroboration can append.
     */
    public static final class Draft {
        public final String field;
        public final Object value;
        public final String label;
        public double confidence;
        public final List<Evidence> evidence;

        Draft(
                String field,
                Object value,
                String label,
                double confidence,
                List<Evidence> evidence) {
            this.field = field;
            this.value = value;
            this.label = label;
            this.confidence = Classifier.round2(confidence);
            this.evidence = new ArrayList<>(evidence);
        }
    }

    public record Result(List<Draft> suggestions, List<Person> newPeople, Client matchedClient) {}

    public record TypeMatch(CatalogItem type, String keyword, boolean inTitle) {}

    private static final Pattern CAPITALISED_NAME =
            Pattern.compile("^([A-Z][\\w'-]+(?:\\s+[A-Z][\\w'-]+){1,2})");
    private static final Pattern TITLE_CLIENT =
            Pattern.compile("^(?:intro(?:\\s+call)?:\\s*)?([A-Z][\\w&]+(?:\\s+[A-Z][\\w&]+)*)");
    private static final Pattern GENERIC_TITLE_WORD =
            Pattern.compile("^(workshop|training|intro|devin)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COPY_SUFFIX =
            Pattern.compile("\\s*\\((?:copy|fwd|forwarded)[^)]*\\)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESTIMATE =
            Pattern.compile(
                    "(?:~|approx\\.?|about|around)?\\s*(\\d{1,3})\\s+(?:new\\s+)?(?:engineers|developers|devs|people|attendees|participants|data engineers)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern GOAL_A =
            Pattern.compile(
                    "(?:goal|focus|topic|objective|aim)s?\\s*(?:is|are)?\\s*[:\\-–]?\\s*([^.\\n]{12,160})",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern GOAL_B =
            Pattern.compile(
                    "\\b(?:rebuild|migrate|retire|move|upgrade|extract|generate|automate)\\b[^.\\n]{8,160}",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+|\\n");

    /* ───────────────────────── helpers ───────────────────────── */

    static boolean containsTerm(String text, String term) {
        String t = term.toLowerCase(Locale.ROOT);
        boolean alnumEdges =
                !t.isEmpty() && asciiAlnum(t.charAt(0)) && asciiAlnum(t.charAt(t.length() - 1));
        String pattern = alnumEdges ? "\\b" + Pattern.quote(t) + "\\b" : Pattern.quote(t);
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    private static boolean asciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    static String nameFromEmail(String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        return Arrays.stream(local.split("[._-]"))
                .filter(p -> !p.isEmpty())
                .map(p -> p.substring(0, 1).toUpperCase(Locale.ROOT) + p.substring(1))
                .collect(Collectors.joining(" "));
    }

    static String companyFromDomain(String domain) {
        String root = domain.contains(".") ? domain.substring(0, domain.indexOf('.')) : domain;
        String s = root.replaceAll("sw$", " Software").replace('-', ' ');
        StringBuilder out = new StringBuilder();
        boolean startOfWord = true;
        for (char c : s.toCharArray()) {
            boolean word = Character.isLetterOrDigit(c) || c == '_';
            out.append(startOfWord && word ? Character.toUpperCase(c) : c);
            startOfWord = !word;
        }
        return out.toString();
    }

    public static TypeMatch matchSessionType(NormalizedEvent n, List<CatalogItem> types) {
        TypeMatch best = null;
        String title = n.title().toLowerCase(Locale.ROOT);
        for (CatalogItem type : types) {
            if (type.archived()) continue;
            for (String keyword : type.keywords()) {
                if (!containsTerm(n.text(), keyword)) continue;
                if (best == null || keyword.length() > best.keyword().length())
                    best = new TypeMatch(type, keyword, containsTerm(title, keyword));
            }
        }
        return best;
    }

    private record RoleMention(String name, String roleId, String excerpt) {}

    private static List<RoleMention> roleMentions(String description, List<CatalogItem> roles) {
        List<RoleMention> out = new ArrayList<>();
        for (CatalogItem role : roles) {
            for (String keyword : role.keywords()) {
                Pattern re =
                        Pattern.compile(
                                "\\b" + Pattern.quote(keyword) + "\\b\\s*[:\\-–]?\\s*",
                                Pattern.CASE_INSENSITIVE);
                Matcher m = re.matcher(description);
                while (m.find()) {
                    String rest = description.substring(m.end());
                    Matcher nm = CAPITALISED_NAME.matcher(rest);
                    if (nm.find()) {
                        String name = nm.group(1).trim();
                        out.add(new RoleMention(name, role.id(), m.group().trim() + " " + name));
                    }
                }
            }
        }
        return out;
    }

    private record Stat(int count, Set<String> by) {}

    /* ───────────────────────── extraction ───────────────────────── */

    public static Result extract(NormalizedEvent n, EngineContext ctx) {
        List<Draft> out = new ArrayList<>();
        Map<String, Member> memberByEmail =
                ctx.members().stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.email().toLowerCase(Locale.ROOT),
                                        m -> m,
                                        (a, b) -> a,
                                        LinkedHashMap::new));
        Map<String, Person> personByEmail = new LinkedHashMap<>();
        for (Person p : ctx.people())
            if (p.email() != null) personByEmail.putIfAbsent(p.email().toLowerCase(Locale.ROOT), p);
        java.util.function.Function<String, String> memberName =
                id ->
                        ctx.members().stream()
                                .filter(m -> m.id().equals(id))
                                .map(Member::name)
                                .findFirst()
                                .orElse("a teammate");

        /* ── Client ── */
        Client client = null;
        Map<String, Integer> domainHits = new LinkedHashMap<>();
        for (NormalizedAttendee a : n.external()) domainHits.merge(a.domain(), 1, Integer::sum);
        List<Map.Entry<String, Integer>> rankedDomains = new ArrayList<>(domainHits.entrySet());
        rankedDomains.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (var entry : rankedDomains) {
            String domain = entry.getKey();
            int count = entry.getValue();
            Client c =
                    ctx.clients().stream()
                            .filter(cl -> cl.domains().contains(domain))
                            .findFirst()
                            .orElse(null);
            if (c == null) continue;
            client = c;
            List<Session> history =
                    ctx.sessions().stream().filter(s -> c.id().equals(s.clientId())).toList();
            List<Evidence> ev = new ArrayList<>();
            ev.add(
                    Evidence.of(
                            "calendar.attendees",
                            count + " attendee" + (count > 1 ? "s" : "") + " @" + domain));
            if (!history.isEmpty()) {
                List<String> names =
                        history.stream()
                                .filter(
                                        s ->
                                                !Objects.equals(
                                                        s.facilitatorId(), ctx.ownerMemberId()))
                                .map(s -> memberName.apply(s.facilitatorId()))
                                .distinct()
                                .limit(2)
                                .toList();
                ev.add(
                        new Evidence(
                                "history.client",
                                history.size()
                                        + " prior session"
                                        + (history.size() > 1 ? "s" : "")
                                        + " with "
                                        + c.name(),
                                names.isEmpty() ? null : "logged by " + String.join(" & ", names)));
            }
            out.add(new Draft("clientId", c.id(), c.name(), 0.96, ev));
            break;
        }
        if (client == null) {
            Client byName =
                    ctx.clients().stream()
                            .filter(c -> containsTerm(n.text(), c.name().split(" ")[0]))
                            .findFirst()
                            .orElse(null);
            if (byName != null) {
                client = byName;
                out.add(
                        new Draft(
                                "clientId",
                                byName.id(),
                                byName.name(),
                                0.86,
                                List.of(Evidence.of("calendar.title", n.title()))));
            } else if (!rankedDomains.isEmpty()) {
                String domain = rankedDomains.getFirst().getKey();
                Matcher tm = TITLE_CLIENT.matcher(n.title());
                String name =
                        tm.find() && !GENERIC_TITLE_WORD.matcher(tm.group(1)).matches()
                                ? tm.group(1)
                                : companyFromDomain(domain);
                out.add(
                        new Draft(
                                "clientId",
                                new ClientCreate(name, domain),
                                "New client: " + name,
                                0.62,
                                List.of(
                                        Evidence.of(
                                                "calendar.attendees", "Unknown domain @" + domain),
                                        Evidence.of("calendar.title", n.title()))));
            }
        }

        /* ── Team ── */
        if (client != null) {
            final Client cl = client;
            var teams = ctx.teams().stream().filter(t -> t.clientId().equals(cl.id())).toList();
            var kw =
                    teams.stream()
                            .filter(
                                    t ->
                                            t.keywords().stream()
                                                            .anyMatch(
                                                                    k -> containsTerm(n.text(), k))
                                                    || containsTerm(n.text(), t.name()))
                            .findFirst()
                            .orElse(null);
            if (kw != null) {
                String hit =
                        kw.keywords().stream()
                                .filter(k -> containsTerm(n.text(), k))
                                .findFirst()
                                .orElse(kw.name());
                out.add(
                        new Draft(
                                "teamId",
                                kw.id(),
                                kw.name(),
                                0.9,
                                List.of(
                                        Evidence.of(
                                                "calendar.title",
                                                "\"" + hit + "\" in the invite"))));
            } else if (teams.size() == 1) {
                out.add(
                        new Draft(
                                "teamId",
                                teams.getFirst().id(),
                                teams.getFirst().name(),
                                0.8,
                                List.of(
                                        Evidence.of(
                                                "history.team",
                                                "Only team recorded for " + cl.name()))));
            } else if (teams.size() > 1) {
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (Session s : ctx.sessions())
                    if (cl.id().equals(s.clientId()) && s.teamId() != null)
                        counts.merge(s.teamId(), 1, Integer::sum);
                var top =
                        counts.entrySet().stream()
                                .max((a, b) -> Integer.compare(a.getValue(), b.getValue()))
                                .orElse(null);
                if (top != null) {
                    var t =
                            teams.stream()
                                    .filter(x -> x.id().equals(top.getKey()))
                                    .findFirst()
                                    .orElse(null);
                    if (t != null) {
                        long total =
                                ctx.sessions().stream()
                                        .filter(s -> cl.id().equals(s.clientId()))
                                        .count();
                        out.add(
                                new Draft(
                                        "teamId",
                                        t.id(),
                                        t.name(),
                                        0.55,
                                        List.of(
                                                Evidence.of(
                                                        "history.team",
                                                        top.getValue()
                                                                + " of "
                                                                + total
                                                                + " "
                                                                + cl.name()
                                                                + " sessions were with "
                                                                + t.name()))));
                    }
                }
            }
        }

        /* ── Title ── */
        String cleanTitle = COPY_SUFFIX.matcher(n.title()).replaceFirst("").trim();
        if (!cleanTitle.isEmpty())
            out.add(
                    new Draft(
                            "title",
                            cleanTitle,
                            cleanTitle,
                            0.95,
                            List.of(Evidence.of("calendar.title", n.title()))));

        /* ── Session type (from the admin-managed table; no guessing) ── */
        TypeMatch typeMatch = matchSessionType(n, ctx.catalogOf("sessionType"));
        if (typeMatch != null) {
            out.add(
                    new Draft(
                            "sessionType",
                            typeMatch.type().id(),
                            typeMatch.type().label(),
                            0.94,
                            List.of(
                                    Evidence.of(
                                            typeMatch.inTitle()
                                                    ? "calendar.title"
                                                    : "calendar.description",
                                            "\"" + typeMatch.keyword() + "\""))));
        }

        /* ── Facilitators ── */
        List<Member> internalMembers =
                n.internal().stream()
                        .map(a -> memberByEmail.get(a.email()))
                        .filter(Objects::nonNull)
                        .toList();
        Member organizerMember =
                n.organizer() != null && n.organizer().isInternal()
                        ? memberByEmail.get(n.organizer().email())
                        : null;
        Member owner =
                ctx.members().stream()
                        .filter(m -> m.id().equals(ctx.ownerMemberId()))
                        .findFirst()
                        .orElseThrow();
        Member facilitator =
                organizerMember != null
                        ? organizerMember
                        : internalMembers.stream().anyMatch(m -> m.id().equals(owner.id()))
                                ? owner
                                : internalMembers.isEmpty() ? owner : internalMembers.getFirst();
        out.add(
                new Draft(
                        "facilitatorId",
                        facilitator.id(),
                        facilitator.name(),
                        organizerMember != null ? 0.95 : 0.9,
                        List.of(
                                organizerMember != null
                                        ? Evidence.of(
                                                "calendar.organizer",
                                                organizerMember.name() + " organised the event")
                                        : Evidence.of(
                                                "calendar.attendees",
                                                facilitator.name() + " is on the invite"))));
        for (Member m : internalMembers) {
            if (m.id().equals(facilitator.id()) || "MANAGER".equals(m.role())) continue;
            out.add(
                    new Draft(
                            "coFacilitatorIds",
                            m.id(),
                            m.name(),
                            0.85,
                            List.of(
                                    Evidence.of(
                                            "calendar.attendees",
                                            m.name() + " is on the invite"))));
        }

        /* ── People: unknown external attendees become people (created with the candidate) ── */
        List<Person> newPeople = new ArrayList<>();
        final Client clientForPeople = client;
        List<Person> externalPeople = new ArrayList<>();
        for (NormalizedAttendee a : n.external()) {
            Person existing = personByEmail.get(a.email());
            if (existing == null) {
                existing =
                        new Person(
                                Ids.stableId("per", a.email()),
                                clientForPeople == null ? null : clientForPeople.id(),
                                a.name() != null ? a.name() : nameFromEmail(a.email()),
                                a.email(),
                                null);
                personByEmail.put(a.email(), existing);
                newPeople.add(existing);
            }
            externalPeople.add(existing);
        }

        /* ── Contacts ── */
        List<CatalogItem> contactRoles = ctx.catalogOf("contactRole");
        java.util.function.Function<String, String> roleLabel =
                roleId ->
                        contactRoles.stream()
                                .filter(r -> r.id().equals(roleId))
                                .map(CatalogItem::label)
                                .findFirst()
                                .orElse(roleId);
        Map<String, Draft> seenContacts = new HashMap<>();
        class Contacts {
            void add(Person person, String roleId, double confidence, List<Evidence> evidence) {
                String key = person.id() + ":" + roleId;
                Draft existing = seenContacts.get(key);
                if (existing != null) {
                    existing.evidence.addAll(evidence);
                    existing.confidence =
                            Classifier.round2(Math.min(0.98, existing.confidence + 0.03));
                    return;
                }
                Draft d =
                        new Draft(
                                "contacts",
                                new SessionContact(person.id(), roleId),
                                person.name() + " · " + roleLabel.apply(roleId),
                                confidence,
                                evidence);
                out.add(d);
                seenContacts.put(key, d);
            }
        }
        Contacts contacts = new Contacts();
        for (RoleMention rm : roleMentions(n.description(), contactRoles)) {
            final Client cl = client;
            Person person =
                    externalPeople.stream()
                            .filter(p -> p.name().equalsIgnoreCase(rm.name()))
                            .findFirst()
                            .orElseGet(
                                    () ->
                                            personByEmail.values().stream()
                                                    .filter(
                                                            p ->
                                                                    p.name()
                                                                                    .equalsIgnoreCase(
                                                                                            rm
                                                                                                    .name())
                                                                            && (cl == null
                                                                                    || Objects
                                                                                            .equals(
                                                                                                    p
                                                                                                            .clientId(),
                                                                                                    cl
                                                                                                            .id())))
                                                    .findFirst()
                                                    .orElse(null));
            if (person != null)
                contacts.add(
                        person,
                        rm.roleId(),
                        0.92,
                        List.of(Evidence.of("calendar.description", "\"" + rm.excerpt() + "\"")));
        }
        if (client != null) {
            final Client cl = client;
            Map<String, Stat> roleStats = new LinkedHashMap<>();
            for (Session s : ctx.sessions()) {
                if (!cl.id().equals(s.clientId())) continue;
                for (SessionContact c : s.contacts()) {
                    String key = c.personId() + ":" + c.roleId();
                    Stat st = roleStats.getOrDefault(key, new Stat(0, new LinkedHashSet<>()));
                    Set<String> by = new LinkedHashSet<>(st.by());
                    if (s.facilitatorId() != null) by.add(s.facilitatorId());
                    roleStats.put(key, new Stat(st.count() + 1, by));
                }
            }
            for (Person p : externalPeople) {
                for (var e : roleStats.entrySet()) {
                    int idx = e.getKey().indexOf(':');
                    String personId = e.getKey().substring(0, idx);
                    String roleId = e.getKey().substring(idx + 1);
                    if (!personId.equals(p.id())) continue;
                    Stat stat = e.getValue();
                    List<String> names = stat.by().stream().map(memberName).toList();
                    contacts.add(
                            p,
                            roleId,
                            Math.min(0.9, 0.7 + 0.07 * stat.count()),
                            List.of(
                                    new Evidence(
                                            "history.people",
                                            "Recorded as "
                                                    + roleLabel.apply(roleId)
                                                    + " on "
                                                    + stat.count()
                                                    + " "
                                                    + cl.name()
                                                    + " session"
                                                    + (stat.count() > 1 ? "s" : ""),
                                            "by "
                                                    + String.join(
                                                            " & ",
                                                            names.stream().limit(2).toList()))));
                }
            }
        }

        /* ── Participants ── */
        if (!externalPeople.isEmpty()) {
            String names =
                    externalPeople.stream()
                                    .limit(4)
                                    .map(Person::name)
                                    .collect(Collectors.joining(", "))
                            + (externalPeople.size() > 4 ? "…" : "");
            out.add(
                    new Draft(
                            "participantIds",
                            externalPeople.stream().map(Person::id).toList(),
                            externalPeople.size()
                                    + " attendee"
                                    + (externalPeople.size() > 1 ? "s" : "")
                                    + (client != null ? " from " + client.name() : ""),
                            0.9,
                            List.of(Evidence.of("calendar.attendees", names))));
        }
        Matcher est = ESTIMATE.matcher(n.description());
        if (est.find()) {
            out.add(
                    new Draft(
                            "participantsEstimated",
                            Integer.parseInt(est.group(1)),
                            "~" + est.group(1) + " people",
                            0.88,
                            List.of(
                                    Evidence.of(
                                            "calendar.description",
                                            "\"" + est.group().trim() + "\""))));
        } else if (externalPeople.size() >= 3) {
            out.add(
                    new Draft(
                            "participantsEstimated",
                            externalPeople.size(),
                            externalPeople.size() + " (from invite)",
                            0.5,
                            List.of(
                                    Evidence.of(
                                            "calendar.attendees",
                                            externalPeople.size()
                                                    + " external attendees accepted"))));
        }

        /* ── Technologies ── */
        List<CatalogItem> technologies = ctx.catalogOf("technology");
        Map<String, String> matchedTech = new LinkedHashMap<>();
        for (CatalogItem tech : technologies) {
            String keyword =
                    tech.keywords().stream()
                            .filter(k -> containsTerm(n.text(), k))
                            .findFirst()
                            .orElse(null);
            if (keyword == null) continue;
            matchedTech.put(tech.id(), keyword);
            boolean exact = keyword.equals(tech.label().toLowerCase(Locale.ROOT));
            out.add(
                    new Draft(
                            "technologyIds",
                            tech.id(),
                            tech.label(),
                            exact ? 0.92 : 0.88,
                            List.of(
                                    Evidence.of(
                                            exact ? "calendar.description" : "taxonomy.alias",
                                            exact
                                                    ? "\"" + tech.label() + "\" in the invite"
                                                    : "\"" + keyword + "\" → " + tech.label()))));
        }
        if (client != null) {
            final Client cl = client;
            List<Session> scope =
                    ctx.sessions().stream().filter(s -> cl.id().equals(s.clientId())).toList();
            Map<String, Stat> stats = new LinkedHashMap<>();
            for (Session s : scope) {
                for (String t : s.technologyIds()) {
                    Stat st = stats.getOrDefault(t, new Stat(0, new LinkedHashSet<>()));
                    Set<String> by = new LinkedHashSet<>(st.by());
                    if (s.facilitatorId() != null) by.add(s.facilitatorId());
                    stats.put(t, new Stat(st.count() + 1, by));
                }
            }
            List<Map.Entry<String, Stat>> ranked = new ArrayList<>(stats.entrySet());
            ranked.sort((a, b) -> Integer.compare(b.getValue().count(), a.getValue().count()));
            for (var e : ranked) {
                if (matchedTech.containsKey(e.getKey())) continue;
                CatalogItem tech =
                        technologies.stream()
                                .filter(t -> t.id().equals(e.getKey()))
                                .findFirst()
                                .orElse(null);
                if (tech == null) continue;
                List<String> names = e.getValue().by().stream().map(memberName).toList();
                out.add(
                        new Draft(
                                "technologyIds",
                                tech.id(),
                                tech.label(),
                                Math.min(0.75, 0.45 + 0.1 * e.getValue().count()),
                                List.of(
                                        new Evidence(
                                                "history.tags",
                                                "Recorded on "
                                                        + e.getValue().count()
                                                        + " of "
                                                        + scope.size()
                                                        + " "
                                                        + cl.name()
                                                        + " session"
                                                        + (scope.size() == 1 ? "" : "s"),
                                                "by "
                                                        + String.join(
                                                                " & ",
                                                                names.stream()
                                                                        .limit(2)
                                                                        .toList())))));
            }
        }

        /* ── Draft use case from the stated goal of the invite ── */
        Matcher goalA = GOAL_A.matcher(n.description());
        Matcher goalB = GOAL_B.matcher(n.description());
        String goalFull = null;
        String goalGroup = null;
        if (goalA.find()) {
            goalFull = goalA.group();
            goalGroup = goalA.group(1);
        } else if (goalB.find()) {
            goalFull = goalB.group();
        }
        if (goalFull != null && !n.external().isEmpty()) {
            String sentence =
                    (goalGroup != null ? goalGroup : goalFull)
                            .trim()
                            .replaceFirst("^[:\\-–\\s]+", "");
            String title =
                    sentence.isEmpty()
                            ? ""
                            : Character.toUpperCase(sentence.charAt(0))
                                    + sentence.substring(1).replaceFirst("[.;]+$", "");
            CatalogItem bestType = null;
            String bestKeyword = null;
            for (CatalogItem type : ctx.catalogOf("useCaseType")) {
                String keyword =
                        type.keywords().stream()
                                .filter(k -> containsTerm(n.text(), k))
                                .findFirst()
                                .orElse(null);
                if (keyword != null
                        && (bestType == null || keyword.length() > bestKeyword.length())) {
                    bestType = type;
                    bestKeyword = keyword;
                }
            }
            UseCase value =
                    new UseCase(
                            Ids.stableId("uc", n.title() + "|" + title),
                            title,
                            "",
                            bestType == null ? null : bestType.id(),
                            new ArrayList<>(matchedTech.keySet()));
            List<Evidence> evidence = new ArrayList<>();
            String excerpt = goalFull.trim();
            evidence.add(
                    Evidence.of(
                            "calendar.description",
                            "\"" + excerpt.substring(0, Math.min(120, excerpt.length())) + "\""));
            if (bestType != null)
                evidence.add(
                        Evidence.of(
                                "taxonomy.alias", "\"" + bestKeyword + "\" → " + bestType.label()));
            out.add(
                    new Draft(
                            "useCases",
                            value,
                            title + (bestType != null ? " · " + bestType.label() : ""),
                            bestType != null ? 0.72 : 0.6,
                            evidence));
        }

        /* ── Draft summary ── */
        if (!n.description().isEmpty() && !n.external().isEmpty()) {
            String firstSentence =
                    SENTENCE_SPLIT
                            .split(n.description(), -1)[0]
                            .replace("[link]", "")
                            .replace("[phone]", "")
                            .trim();
            if (firstSentence.length() > 12) {
                String draft = (client != null ? client.name() + ": " : "") + firstSentence;
                out.add(
                        new Draft(
                                "summary",
                                draft,
                                "Draft summary from the invite",
                                0.55,
                                List.of(
                                        Evidence.of(
                                                "calendar.description",
                                                "\"" + firstSentence + "\""))));
            }
        }

        return new Result(out, newPeople, client);
    }
}
