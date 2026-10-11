package com.cognition.est.calendar;

import com.cognition.est.domain.model.Attendee;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * "Google Calendar" for the local profile — a port of web/lib/mock/calendar.ts. Each persona has
 * fixture batches: cursor 0 is the initial bounded lookback, later cursors are incremental syncs
 * (including an updated event, a forwarded duplicate and noise that must never be classified). The
 * sync token is simply the next cursor. Dates are relative to Monday of the current week; two
 * events are pinned to the actual current day so the Today page always has something to show.
 */
@Component
@ConditionalOnProperty(prefix = "est.google", name = "mock", havingValue = "true")
public class MockCalendarProvider implements CalendarProvider {

    public static final String TZ = "America/New_York";
    private static final ZoneId ZONE = ZoneId.of(TZ);
    private static final String ALEX = "alex@cognition.ai",
            PRIYA = "priya@cognition.ai",
            MARCUS = "marcus@cognition.ai",
            SOFIA = "sofia@cognition.ai",
            DANA = "dana@cognition.ai";
    private static final String MEET = "https://meet.google.com/abc-defg-hij";
    private static final Object TODAY = "today";

    private final MemberRepository members;
    private final PersonRepository people;
    private final AtomicReference<String> scenario = new AtomicReference<>("messy");

    public MockCalendarProvider(MemberRepository members, PersonRepository people) {
        this.members = members;
        this.people = people;
    }

    public void setScenario(String s) {
        scenario.set(s);
    }

    public String scenario() {
        return scenario.get();
    }

    @Override
    public String name() {
        return "mock";
    }

    /* ───────────────────────── specs ───────────────────────── */

    record Spec(
            String id,
            String title,
            String desc,
            Object day,
            int hour,
            int minute,
            int duration,
            String organizer,
            List<String> attendees,
            String conferencing,
            String location,
            String recurring,
            String status,
            String etag) {
        Spec withId(String newId) {
            return new Spec(
                    newId,
                    title,
                    desc,
                    day,
                    hour,
                    minute,
                    duration,
                    organizer,
                    attendees,
                    conferencing,
                    location,
                    recurring,
                    status,
                    etag);
        }

        Spec withDesc(String d, String e) {
            return new Spec(
                    id,
                    title,
                    d,
                    day,
                    hour,
                    minute,
                    duration,
                    organizer,
                    attendees,
                    conferencing,
                    location,
                    recurring,
                    status,
                    e);
        }

        Spec withAttendees(List<String> a) {
            return new Spec(
                    id,
                    title,
                    desc,
                    day,
                    hour,
                    minute,
                    duration,
                    organizer,
                    a,
                    conferencing,
                    location,
                    recurring,
                    status,
                    etag);
        }
    }

    private static Spec s(
            String id,
            String title,
            String desc,
            Object day,
            int hour,
            int duration,
            String organizer,
            List<String> attendees,
            String conferencing,
            String location) {
        return new Spec(
                id,
                title,
                desc,
                day,
                hour,
                0,
                duration,
                organizer,
                attendees,
                conferencing,
                location,
                null,
                null,
                null);
    }

    private static Spec standup(String id, int day) {
        return new Spec(
                id,
                "Enablement team standup",
                null,
                day,
                9,
                15,
                15,
                DANA,
                List.of(ALEX, PRIYA, MARCUS, SOFIA, DANA),
                null,
                null,
                "rec-standup",
                null,
                null);
    }

    static final List<Spec> ALEX_LIKELY =
            List.of(
                    s(
                            "evt-mb-101-today",
                            "Mercedes-Benz Data Engineering — Devin 101 session (Ab Initio squad)",
                            "101 session for the Ab Initio squad. Goal: retire Ab Initio graphs by moving the ingestion pipelines to PySpark on Databricks. PO: Katrin Vogel. Architect: Jonas Keller. ~12 engineers.\n\nJoin: "
                                    + MEET,
                            TODAY,
                            10,
                            90,
                            "katrin.vogel@mercedes-benz.com",
                            List.of(
                                    ALEX,
                                    "katrin.vogel@mercedes-benz.com",
                                    "jonas.keller@mercedes-benz.com",
                                    "ravi.menon@mercedes-benz.com",
                                    "lukas.brandt@mercedes-benz.com",
                                    "mei.lin@mercedes-benz.com"),
                            MEET,
                            null),
                    s(
                            "evt-mb-pair-today",
                            "Mercedes-Benz — Pair programming: fleet KPI Excel to Power BI",
                            "Pairing session with Ravi on the fleet KPI spreadsheets: rebuild the Excel macros as a Power BI dashboard fed from Databricks. EM: Anika Schulz.",
                            TODAY,
                            14,
                            90,
                            ALEX,
                            List.of(
                                    ALEX,
                                    "ravi.menon@mercedes-benz.com",
                                    "anika.schulz@mercedes-benz.com"),
                            MEET,
                            null),
                    s(
                            "evt-acme-oh-1",
                            "Acme Platform — Devin 101 session (Jenkins squad)",
                            "101 session for the Jenkins squad joining the platform rollout. Topics: Java 21 upgrade stragglers, Jenkins pipeline credentials. PO: Jane Doe.\n\nJoin: "
                                    + MEET
                                    + "\nPasscode: 4421",
                            -3,
                            10,
                            90,
                            ALEX,
                            List.of(
                                    ALEX,
                                    "jane.doe@acmefinancial.com",
                                    "lena.fischer@acmefinancial.com",
                                    "omar.haddad@acmefinancial.com"),
                            MEET,
                            null),
                    s(
                            "evt-vantage-d1",
                            "Vantage Claims — Sprint in a day",
                            "Sprint in a day with ~20 engineers from the claims modernization team. Focus: COBOL claims engine documentation and Java unit tests with Devin. Sponsor: Nadia Rahman. Architect: Felix Brandt.\n\nDial-in: +1 555 010 2233 PIN 889 221",
                            1,
                            9,
                            360,
                            "zoe.martin@vantage-ins.com",
                            List.of(
                                    ALEX,
                                    MARCUS,
                                    "zoe.martin@vantage-ins.com",
                                    "felix.brandt@vantage-ins.com",
                                    "nadia.rahman@vantage-ins.com",
                                    "priyanka.rao@vantage-ins.com",
                                    "ahmed.saleh@vantage-ins.com",
                                    "julia.moreau@vantage-ins.com"),
                            null,
                            "Vantage HQ, 12th floor"),
                    s(
                            "evt-vantage-d2",
                            "Vantage Claims — 201 session: playbooks & review",
                            "201 session the day after the sprint: Devin Playbooks for COBOL documentation and Devin Review on the claims-rules service. Architect: Felix Brandt.",
                            2,
                            9,
                            150,
                            "zoe.martin@vantage-ins.com",
                            List.of(
                                    ALEX,
                                    MARCUS,
                                    "zoe.martin@vantage-ins.com",
                                    "felix.brandt@vantage-ins.com",
                                    "priyanka.rao@vantage-ins.com",
                                    "ahmed.saleh@vantage-ins.com",
                                    "julia.moreau@vantage-ins.com"),
                            null,
                            "Vantage HQ, 12th floor"),
                    s(
                            "evt-northwind-fu",
                            "Northwind — Pair programming: microservices decomposition",
                            "Pairing session on the WMS decomposition. Work Devin's proposed bounded-context split and the GitLab CI changes on a real ticket. PO: Maya Okafor.",
                            3,
                            14,
                            90,
                            ALEX,
                            List.of(
                                    ALEX,
                                    SOFIA,
                                    "maya.okafor@northwind.io",
                                    "ben.carter@northwind.io"),
                            MEET,
                            null),
                    s(
                            "evt-meridian-training",
                            "Meridian Data Platform — Devin 101 session for new joiners",
                            "101 session for 8 new data engineers on Devin and the Jira bug-triage workflow for the ingestion pipelines (Python / Postgres / GCP). EM: Victor Mensah.",
                            4,
                            11,
                            90,
                            "victor.mensah@meridianhealth.org",
                            List.of(
                                    ALEX,
                                    "victor.mensah@meridianhealth.org",
                                    "luis.ortega@meridianhealth.org",
                                    "amara.osei@meridianhealth.org",
                                    "ken.watanabe@meridianhealth.org",
                                    "sara.lindqvist@meridianhealth.org"),
                            MEET,
                            null),
                    s(
                            "evt-acme-pay-review",
                            "Acme Payments — Ask me anything: Devin Review rollout",
                            "AMA while rolling out Devin Review to all Kafka consumer repos (Kotlin). Architect: Raj Patel. ~10 engineers from Payments.",
                            9,
                            14,
                            45,
                            MARCUS,
                            List.of(
                                    ALEX,
                                    MARCUS,
                                    "chris.lowe@acmefinancial.com",
                                    "yuki.tanaka@acmefinancial.com",
                                    "raj.patel@acmefinancial.com"),
                            MEET,
                            null));

    static final List<Spec> ALEX_REVIEW =
            List.of(
                    s(
                            "evt-helios-generic-ws",
                            "Helios Grid — enablement workshop",
                            "Working session with the grid software team on Devin adoption. Architect: Aisha Bello.",
                            7,
                            13,
                            90,
                            ALEX,
                            List.of(
                                    ALEX,
                                    "aisha.bello@helios-energy.com",
                                    "noah.kim@helios-energy.com",
                                    "sam.whitaker@helios-energy.com"),
                            MEET,
                            null),
                    s(
                            "evt-brightline-intro",
                            "Intro call: Brightline Software <> Cognition",
                            "Intro to Devin for Brightline's platform team. They run Go services on GitHub Actions and want help with dependency upgrades.",
                            8,
                            15,
                            45,
                            DANA,
                            List.of(
                                    ALEX,
                                    DANA,
                                    "kate.morgan@brightlinesw.com",
                                    "ravi.shah@brightlinesw.com"),
                            MEET,
                            null),
                    s(
                            "evt-helios-sync",
                            "Helios Grid — enablement sync",
                            null,
                            -1,
                            13,
                            30,
                            ALEX,
                            List.of(ALEX, "sam.whitaker@helios-energy.com"),
                            MEET,
                            null));

    static final List<Spec> ALEX_MESSY_EXTRAS =
            List.of(
                    s(
                            "evt-vantage-d1-copy",
                            "Vantage Claims Sprint in a day (copy)",
                            "Forwarded from Zoe's calendar.",
                            1,
                            9,
                            360,
                            "zoe.martin@vantage-ins.com",
                            List.of(
                                    ALEX,
                                    "zoe.martin@vantage-ins.com",
                                    "felix.brandt@vantage-ins.com",
                                    "nadia.rahman@vantage-ins.com"),
                            null,
                            "Vantage HQ, 12th floor"),
                    s(
                            "evt-incomplete-workshop",
                            "Workshop",
                            null,
                            10,
                            10,
                            90,
                            "ingrid.solberg@northwind.io",
                            List.of(ALEX, "ingrid.solberg@northwind.io"),
                            null,
                            null),
                    new Spec(
                            "evt-orbital-cancelled",
                            "Orbital Storefront — Ask me anything",
                            "Cancelled: Pavel out this week.",
                            -2,
                            10,
                            0,
                            60,
                            SOFIA,
                            List.of(ALEX, SOFIA, "pavel.novak@orbitalretail.com"),
                            null,
                            null,
                            null,
                            "cancelled",
                            null));

    static final List<Spec> ALEX_NOISE =
            List.of(
                    s(
                            "evt-1on1",
                            "Alex / Dana 1:1",
                            null,
                            -4,
                            16,
                            30,
                            DANA,
                            List.of(ALEX, DANA),
                            null,
                            null),
                    standup("evt-standup-1", -3),
                    standup("evt-standup-2", 0),
                    standup("evt-standup-3", 3),
                    s(
                            "evt-interview",
                            "Interview — Applied AI Engineer candidate",
                            "Panel interview, system design round.",
                            5,
                            15,
                            60,
                            "recruiting@cognition.ai",
                            List.of(ALEX, SOFIA, "recruiting@cognition.ai"),
                            null,
                            null),
                    s("evt-focus", "Focus time", null, 0, 12, 60, ALEX, List.of(ALEX), null, null),
                    s("evt-dentist", "Dentist", null, 6, 8, 60, ALEX, List.of(ALEX), null, null));

    private static Spec byId(String id) {
        return ALEX_LIKELY.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    static List<Spec> alexBatch1(boolean updatedDesc) {
        Spec acme = byId("evt-acme-oh-1");
        return List.of(
                updatedDesc
                        ? acme.withDesc(
                                acme.desc()
                                        + "\n\nUpdate: Architect Raj Patel joining to cover the Jenkins pipeline migration.",
                                "etag-evt-acme-oh-1-2")
                        : acme,
                byId("evt-northwind-fu"),
                s(
                        "evt-helios-vdi-ws",
                        "Helios Grid — Pair programming (VDI environment)",
                        "Second attempt at pairing in the restricted network. Egress allow-list approved. Python services on Kubernetes; goal is unit test generation. PM: Sam Whitaker. Architect: Aisha Bello.",
                        11,
                        13,
                        120,
                        "sam.whitaker@helios-energy.com",
                        List.of(
                                ALEX,
                                "sam.whitaker@helios-energy.com",
                                "aisha.bello@helios-energy.com",
                                "noah.kim@helios-energy.com",
                                "li.wei@helios-energy.com"),
                        null,
                        "Helios Operations Center"),
                s(
                        "evt-meridian-clinical-fu",
                        "Meridian Clinical — 201 session: Angular migration",
                        "201 session to finish the remaining 5 components now that Bitbucket SSO is sorted. PO: Grace Liu.",
                        12,
                        11,
                        75,
                        PRIYA,
                        List.of(
                                ALEX,
                                PRIYA,
                                "grace.liu@meridianhealth.org",
                                "hannah.weiss@meridianhealth.org"),
                        MEET,
                        null));
    }

    static final List<Spec> ALEX_BATCH2 =
            List.of(
                    s(
                            "evt-orbital-q4-kickoff",
                            "Orbital Storefront — Sprint in a day: Q4 pilot",
                            "Pilot approved. Sprint in a day with the wider storefront team (~12 engineers). React / TypeScript / Node; Slack-triggered feature work. PO: Pavel Novak.",
                            15,
                            9,
                            360,
                            SOFIA,
                            List.of(
                                    ALEX,
                                    SOFIA,
                                    "pavel.novak@orbitalretail.com",
                                    "emma.clarke@orbitalretail.com",
                                    "jon.park@orbitalretail.com",
                                    "mia.rossi@orbitalretail.com"),
                            MEET,
                            null),
                    s(
                            "evt-northwind-readout",
                            "Northwind — exec readout",
                            "Readout to Northwind leadership on pilot results.",
                            16,
                            16,
                            30,
                            SOFIA,
                            List.of(ALEX, SOFIA, DANA, "maya.okafor@northwind.io"),
                            MEET,
                            null));

    static final List<Spec> PRIYA_BATCH0 =
            List.of(
                    s(
                            "evt-p-meridian-ama-today",
                            "Meridian Clinical — Ask me anything",
                            "AMA with the clinical apps team after the Angular migration. PO: Grace Liu.",
                            TODAY,
                            11,
                            45,
                            PRIYA,
                            List.of(
                                    PRIYA,
                                    "grace.liu@meridianhealth.org",
                                    "hannah.weiss@meridianhealth.org"),
                            MEET,
                            null),
                    s(
                            "evt-p-meridian-clinical-d2",
                            "Meridian Clinical — 201 session (part 2)",
                            "201 session: continue the AngularJS → Angular migration of the scheduling app. PO: Grace Liu. ~11 engineers.",
                            2,
                            11,
                            75,
                            PRIYA,
                            List.of(
                                    PRIYA,
                                    "grace.liu@meridianhealth.org",
                                    "hannah.weiss@meridianhealth.org",
                                    "luis.ortega@meridianhealth.org"),
                            MEET,
                            null),
                    s(
                            "evt-p-acme-oh",
                            "Acme Platform — Ask me anything",
                            "AMA. Java 21 stragglers. PO: Jane Doe.",
                            4,
                            10,
                            45,
                            PRIYA,
                            List.of(
                                    PRIYA,
                                    "jane.doe@acmefinancial.com",
                                    "lena.fischer@acmefinancial.com"),
                            MEET,
                            null),
                    standup("evt-p-standup", 0),
                    s(
                            "evt-p-1on1",
                            "Priya / Dana 1:1",
                            null,
                            1,
                            16,
                            30,
                            DANA,
                            List.of(PRIYA, DANA),
                            null,
                            null));

    static final List<Spec> MARCUS_BATCH0 =
            List.of(
                    byId("evt-acme-pay-review").withId("evt-m-acme-pay-review"),
                    s(
                            "evt-m-helios-oh",
                            "Helios Grid — Ask me anything",
                            "AMA after the blocked pairing session. Architect: Aisha Bello.",
                            5,
                            13,
                            45,
                            MARCUS,
                            List.of(
                                    MARCUS,
                                    "aisha.bello@helios-energy.com",
                                    "noah.kim@helios-energy.com"),
                            MEET,
                            null),
                    standup("evt-m-standup", 0));

    static final List<Spec> SOFIA_BATCH0 =
            List.of(
                    ALEX_BATCH2.getFirst().withId("evt-s-orbital-q4-kickoff"),
                    s(
                            "evt-s-northwind-ws",
                            "Northwind WMS — Pair programming: service extraction",
                            "Pairing: extract the first bounded context (inventory) with Devin. .NET / Azure / GitLab. Architect: Ben Carter. ~10 engineers.",
                            8,
                            9,
                            120,
                            SOFIA,
                            List.of(
                                    SOFIA,
                                    "maya.okafor@northwind.io",
                                    "ben.carter@northwind.io",
                                    "ingrid.solberg@northwind.io",
                                    "diego.ruiz@northwind.io"),
                            null,
                            "Northwind Rotterdam office"),
                    standup("evt-s-standup", 0));

    static final List<Spec> DANA_BATCH0 =
            List.of(
                    standup("evt-d-standup", 0),
                    ALEX_REVIEW
                            .get(1)
                            .withId("evt-d-brightline")
                            .withAttendees(
                                    List.of(
                                            DANA,
                                            ALEX,
                                            "kate.morgan@brightlinesw.com",
                                            "ravi.shah@brightlinesw.com")));

    static Map<String, List<List<Spec>>> batches(String scenario) {
        if ("empty".equals(scenario)) return Map.of();
        boolean messy = "messy".equals(scenario);
        List<Spec> first = new ArrayList<>(ALEX_LIKELY);
        first.addAll(ALEX_REVIEW);
        if (messy) first.addAll(ALEX_MESSY_EXTRAS);
        first.addAll(ALEX_NOISE);
        return Map.of(
                ALEX,
                List.of(first, alexBatch1(messy), ALEX_BATCH2),
                PRIYA,
                List.of(PRIYA_BATCH0),
                MARCUS,
                List.of(MARCUS_BATCH0),
                SOFIA,
                List.of(SOFIA_BATCH0),
                DANA,
                List.of(DANA_BATCH0));
    }

    public int remainingPages(String email) {
        List<List<Spec>> b =
                batches(scenario.get()).getOrDefault(email.toLowerCase(Locale.ROOT), List.of());
        return b.size();
    }

    /* ───────────────────────── provider ───────────────────────── */

    @Override
    public Page fetch(IdentityRef identity, Member owner, Instant now) {
        List<List<Spec>> all =
                batches(scenario.get())
                        .getOrDefault(identity.email().toLowerCase(Locale.ROOT), List.of());
        int cursor = identity.syncToken() == null ? 0 : Integer.parseInt(identity.syncToken());
        List<Spec> page = cursor < all.size() ? all.get(cursor) : List.of();
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        LocalDate anchor = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int todayOffset = (int) ChronoUnit.DAYS.between(anchor, today);
        List<ProviderEvent> events =
                page.stream()
                        .map(sp -> toProviderEvent(anchor, sp, todayOffset, identity.memberId()))
                        .toList();
        return new Page(events, String.valueOf(Math.min(cursor + 1, all.size())), false);
    }

    private ProviderEvent toProviderEvent(
            LocalDate anchor, Spec sp, int todayOffset, String memberId) {
        int day = sp.day() instanceof Integer i ? i : todayOffset;
        Instant start =
                anchor.plusDays(day)
                        .atTime(LocalTime.of(sp.hour(), sp.minute()))
                        .atZone(ZONE)
                        .toInstant();
        List<Attendee> attendees =
                sp.attendees().stream()
                        .map(
                                email ->
                                        new Attendee(
                                                email,
                                                nameFor(email),
                                                "accepted",
                                                email.equals(sp.organizer())))
                        .toList();
        return new ProviderEvent(
                sp.id(),
                sp.recurring(),
                sp.title(),
                sp.desc() == null ? "" : sp.desc(),
                sp.location(),
                sp.conferencing(),
                start,
                start.plusSeconds(sp.duration() * 60L),
                TZ,
                sp.organizer(),
                attendees,
                sp.etag() != null ? sp.etag() : "etag-" + sp.id() + "-1",
                sp.status() != null ? sp.status() : "confirmed",
                null);
    }

    private String nameFor(String email) {
        String e = email.toLowerCase(Locale.ROOT);
        var member = members.findByEmail(e);
        if (member.isPresent()) return member.get().name();
        var person =
                people.findAll("org_cognition").stream()
                        .filter(p -> p.email() != null && p.email().equalsIgnoreCase(e))
                        .findFirst();
        if (person.isPresent()) return person.get().name();
        String local = e.substring(0, e.indexOf('@'));
        if (!local.contains(".")) return null;
        StringBuilder sb = new StringBuilder();
        for (String part : local.split("\\.")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
