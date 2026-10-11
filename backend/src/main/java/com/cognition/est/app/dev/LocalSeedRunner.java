package com.cognition.est.app.dev;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-profile demo data: 5 personas, 7 clients with teams and contacts, and 16 historical
 * sessions logged by other engineers (the fuel for cross-user suggestions). Mirrors
 * web/lib/mock/seed.ts; dates are relative to Monday of the current week so the demo is always
 * fresh. Idempotent: skipped when members already exist.
 */
@Component
@Profile("local")
public class LocalSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedRunner.class);
    public static final String ORG = "org_cognition";
    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    private final JdbcClient jdbc;

    public LocalSeedRunner(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.sql("select count(*) from members").query(Integer.class).single();
        if (existing != null && existing > 0) {
            log.info("local seed: members already present, skipping");
            return;
        }
        Instant now = Instant.now();
        seedMembers(now);
        seedClients(now);
        seedPeople(now);
        seedHistory(now);
        jdbc.sql("insert into local_seed_marker (applied_at) values (:t)").param("t", now).update();
        log.info(
                "local seed: applied (5 members, 7 clients, {} people, {} historical sessions)",
                PEOPLE.size(),
                HISTORY.size());
    }

    /* ─────────────────────────── members ─────────────────────────── */

    private record MemberSpec(
            String id, String name, String email, String title, String role, int hue) {}

    static final List<MemberSpec> MEMBERS =
            List.of(
                    new MemberSpec(
                            "mem_alex",
                            "Alex Rivera",
                            "alex@cognition.ai",
                            "Applied AI Engineer",
                            "MEMBER",
                            262),
                    new MemberSpec(
                            "mem_priya",
                            "Priya Nair",
                            "priya@cognition.ai",
                            "Applied AI Engineer",
                            "MEMBER",
                            330),
                    new MemberSpec(
                            "mem_marcus",
                            "Marcus Chen",
                            "marcus@cognition.ai",
                            "Applied AI Engineer",
                            "MEMBER",
                            160),
                    new MemberSpec(
                            "mem_sofia",
                            "Sofia Alvarez",
                            "sofia@cognition.ai",
                            "Senior Applied AI Engineer",
                            "MEMBER",
                            25),
                    new MemberSpec(
                            "mem_dana",
                            "Dana Whitfield",
                            "dana@cognition.ai",
                            "Head of Enablement",
                            "ADMIN",
                            200));

    private void seedMembers(Instant now) {
        for (var m : MEMBERS) {
            jdbc.sql(
                            """
                    insert into members (id, organization_id, google_sub, email, name, title, role, status, hue, time_zone, created_at, updated_at)
                    values (:id, :org, :sub, :email, :name, :title, :role, 'ACTIVE', :hue, 'America/New_York', :now, :now)
                    """)
                    .param("id", m.id())
                    .param("org", ORG)
                    .param("sub", "mock-" + m.id())
                    .param("email", m.email())
                    .param("name", m.name())
                    .param("title", m.title())
                    .param("role", m.role())
                    .param("hue", m.hue())
                    .param("now", now)
                    .update();
            // One mock "Google" identity per persona so calendar sync works without credentials.
            jdbc.sql(
                            """
                    insert into member_identities (id, organization_id, member_id, provider, provider_sub, email, display_name, scopes, status, created_at, updated_at)
                    values (:id, :org, :m, 'mock', :sub, :email, :name, 'calendar.readonly', 'ACTIVE', :now, :now)
                    """)
                    .param("id", "idn_" + m.id())
                    .param("org", ORG)
                    .param("m", m.id())
                    .param("sub", "mock-" + m.id())
                    .param("email", m.email())
                    .param("name", m.name())
                    .param("now", now)
                    .update();
        }
    }

    /* ─────────────────────────── clients & teams ─────────────────────────── */

    private record ClientSpec(
            String id,
            String name,
            List<String> domains,
            String industry,
            int hue,
            List<TeamSpec> teams) {}

    private record TeamSpec(String id, String name, List<String> keywords) {}

    static final List<ClientSpec> CLIENTS =
            List.of(
                    new ClientSpec(
                            "cli_acme",
                            "Acme Financial",
                            List.of("acmefinancial.com", "acme-fin.com"),
                            "Financial services",
                            220,
                            List.of(
                                    new TeamSpec(
                                            "team_acme_platform",
                                            "Platform Engineering",
                                            List.of("platform", "core platform")),
                                    new TeamSpec(
                                            "team_acme_payments",
                                            "Payments",
                                            List.of("payments", "pay")))),
                    new ClientSpec(
                            "cli_northwind",
                            "Northwind Logistics",
                            List.of("northwind.io"),
                            "Logistics",
                            150,
                            List.of(
                                    new TeamSpec(
                                            "team_nw_fulfilment",
                                            "Fulfilment Engineering",
                                            List.of("fulfilment", "fulfillment", "wms")))),
                    new ClientSpec(
                            "cli_helios",
                            "Helios Energy",
                            List.of("helios-energy.com"),
                            "Energy",
                            40,
                            List.of(
                                    new TeamSpec(
                                            "team_helios_grid",
                                            "Grid Software",
                                            List.of("grid", "scada")))),
                    new ClientSpec(
                            "cli_meridian",
                            "Meridian Health",
                            List.of("meridianhealth.org"),
                            "Healthcare",
                            300,
                            List.of(
                                    new TeamSpec(
                                            "team_mer_clinical",
                                            "Clinical Apps",
                                            List.of("clinical", "ehr")),
                                    new TeamSpec(
                                            "team_mer_data",
                                            "Data Platform",
                                            List.of("data platform", "analytics", "data eng")))),
                    new ClientSpec(
                            "cli_orbital",
                            "Orbital Retail",
                            List.of("orbitalretail.com"),
                            "Retail",
                            10,
                            List.of(
                                    new TeamSpec(
                                            "team_orb_storefront",
                                            "Storefront",
                                            List.of("storefront", "web")))),
                    new ClientSpec(
                            "cli_vantage",
                            "Vantage Insurance",
                            List.of("vantage-ins.com"),
                            "Insurance",
                            190,
                            List.of(
                                    new TeamSpec(
                                            "team_van_claims",
                                            "Claims Modernization",
                                            List.of("claims", "modernization", "modernisation")))),
                    new ClientSpec(
                            "cli_mercedes",
                            "Mercedes-Benz Tech",
                            List.of("mercedes-benz.com", "mbtech.io"),
                            "Automotive",
                            95,
                            List.of(
                                    new TeamSpec(
                                            "team_mb_data",
                                            "Data Engineering",
                                            List.of(
                                                    "data engineering",
                                                    "data eng",
                                                    "ab initio",
                                                    "etl")),
                                    new TeamSpec(
                                            "team_mb_car",
                                            "Connected Car",
                                            List.of("connected car", "telematics")))));

    private void seedClients(Instant now) {
        for (var c : CLIENTS) {
            jdbc.sql(
                            """
                    insert into clients (id, organization_id, name, industry, hue, archived, version, created_at, updated_at, updated_by)
                    values (:id, :org, :name, :industry, :hue, false, 0, :now, :now, null)
                    """)
                    .param("id", c.id())
                    .param("org", ORG)
                    .param("name", c.name())
                    .param("industry", c.industry())
                    .param("hue", c.hue())
                    .param("now", now)
                    .update();
            for (String d : c.domains()) {
                jdbc.sql("insert into client_domains (client_id, domain) values (:c, :d)")
                        .param("c", c.id())
                        .param("d", d)
                        .update();
            }
            for (var t : c.teams()) {
                jdbc.sql(
                                """
                        insert into teams (id, organization_id, client_id, name, keywords, archived, created_at, updated_at)
                        values (:id, :org, :client, :name, :kw, false, :now, :now)
                        """)
                        .param("id", t.id())
                        .param("org", ORG)
                        .param("client", c.id())
                        .param("name", t.name())
                        .param("kw", String.join("|", t.keywords()))
                        .param("now", now)
                        .update();
            }
        }
    }

    /* ─────────────────────────── people ─────────────────────────── */

    private record PersonSpec(String client, String name, String email, String role) {}

    static final List<PersonSpec> PEOPLE =
            List.of(
                    new PersonSpec(
                            "cli_acme", "Jane Doe", "jane.doe@acmefinancial.com", "productOwner"),
                    new PersonSpec(
                            "cli_acme", "Raj Patel", "raj.patel@acmefinancial.com", "architect"),
                    new PersonSpec(
                            "cli_acme",
                            "Tom Becker",
                            "tom.becker@acmefinancial.com",
                            "projectManager"),
                    new PersonSpec(
                            "cli_acme",
                            "Lena Fischer",
                            "lena.fischer@acmefinancial.com",
                            "engineer"),
                    new PersonSpec(
                            "cli_acme", "Omar Haddad", "omar.haddad@acmefinancial.com", "engineer"),
                    new PersonSpec(
                            "cli_acme", "Yuki Tanaka", "yuki.tanaka@acmefinancial.com", "engineer"),
                    new PersonSpec(
                            "cli_acme",
                            "Chris Lowe",
                            "chris.lowe@acmefinancial.com",
                            "engineeringManager"),
                    new PersonSpec(
                            "cli_northwind",
                            "Maya Okafor",
                            "maya.okafor@northwind.io",
                            "productOwner"),
                    new PersonSpec(
                            "cli_northwind", "Ben Carter", "ben.carter@northwind.io", "architect"),
                    new PersonSpec(
                            "cli_northwind",
                            "Ingrid Solberg",
                            "ingrid.solberg@northwind.io",
                            "engineer"),
                    new PersonSpec(
                            "cli_northwind", "Diego Ruiz", "diego.ruiz@northwind.io", "engineer"),
                    new PersonSpec(
                            "cli_helios",
                            "Sam Whitaker",
                            "sam.whitaker@helios-energy.com",
                            "projectManager"),
                    new PersonSpec(
                            "cli_helios",
                            "Aisha Bello",
                            "aisha.bello@helios-energy.com",
                            "architect"),
                    new PersonSpec(
                            "cli_helios", "Noah Kim", "noah.kim@helios-energy.com", "engineer"),
                    new PersonSpec(
                            "cli_meridian",
                            "Grace Liu",
                            "grace.liu@meridianhealth.org",
                            "productOwner"),
                    new PersonSpec(
                            "cli_meridian",
                            "Victor Mensah",
                            "victor.mensah@meridianhealth.org",
                            "engineeringManager"),
                    new PersonSpec(
                            "cli_meridian",
                            "Hannah Weiss",
                            "hannah.weiss@meridianhealth.org",
                            "engineer"),
                    new PersonSpec(
                            "cli_meridian",
                            "Luis Ortega",
                            "luis.ortega@meridianhealth.org",
                            "engineer"),
                    new PersonSpec(
                            "cli_orbital",
                            "Pavel Novak",
                            "pavel.novak@orbitalretail.com",
                            "productOwner"),
                    new PersonSpec(
                            "cli_orbital",
                            "Emma Clarke",
                            "emma.clarke@orbitalretail.com",
                            "engineer"),
                    new PersonSpec(
                            "cli_vantage",
                            "Nadia Rahman",
                            "nadia.rahman@vantage-ins.com",
                            "executiveSponsor"),
                    new PersonSpec(
                            "cli_vantage",
                            "Felix Brandt",
                            "felix.brandt@vantage-ins.com",
                            "architect"),
                    new PersonSpec(
                            "cli_vantage",
                            "Zoe Martin",
                            "zoe.martin@vantage-ins.com",
                            "projectManager"),
                    new PersonSpec(
                            "cli_mercedes",
                            "Katrin Vogel",
                            "katrin.vogel@mercedes-benz.com",
                            "productOwner"),
                    new PersonSpec(
                            "cli_mercedes",
                            "Jonas Keller",
                            "jonas.keller@mercedes-benz.com",
                            "architect"),
                    new PersonSpec(
                            "cli_mercedes",
                            "Anika Schulz",
                            "anika.schulz@mercedes-benz.com",
                            "engineeringManager"),
                    new PersonSpec(
                            "cli_mercedes",
                            "Ravi Menon",
                            "ravi.menon@mercedes-benz.com",
                            "engineer"),
                    new PersonSpec(
                            "cli_mercedes",
                            "Lukas Brandt",
                            "lukas.brandt@mercedes-benz.com",
                            "engineer"));

    static String personId(String email) {
        return "per_" + email.substring(0, email.indexOf('@')).replace('.', '_');
    }

    private void seedPeople(Instant now) {
        for (var p : PEOPLE) {
            jdbc.sql(
                            """
                    insert into people (id, organization_id, client_id, name, email, role_id, created_at, updated_at)
                    values (:id, :org, :client, :name, :email, :role, :now, :now)
                    """)
                    .param("id", personId(p.email()))
                    .param("org", ORG)
                    .param("client", p.client())
                    .param("name", p.name())
                    .param("email", p.email())
                    .param("role", p.role())
                    .param("now", now)
                    .update();
        }
    }

    /* ─────────────────────────── historical sessions ─────────────────────────── */

    private record UseCaseSpec(String title, String type, List<String> tech) {}

    private record HistorySpec(
            String by,
            String client,
            String team,
            int dayOffset,
            int hour,
            int minutes,
            String type,
            String title,
            Map<String, String> contacts,
            List<String> participants,
            Integer estimated,
            List<String> tech,
            String outcome,
            List<UseCaseSpec> useCases,
            String summary) {}

    static final List<HistorySpec> HISTORY =
            List.of(
                    new HistorySpec(
                            "mem_priya",
                            "cli_acme",
                            "team_acme_platform",
                            -42,
                            10,
                            90,
                            "101",
                            "Acme Platform — Devin 101 session",
                            Map.of(
                                    "jane.doe@acmefinancial.com",
                                    "productOwner",
                                    "raj.patel@acmefinancial.com",
                                    "architect",
                                    "tom.becker@acmefinancial.com",
                                    "projectManager"),
                            List.of(
                                    "lena.fischer@acmefinancial.com",
                                    "omar.haddad@acmefinancial.com"),
                            12,
                            List.of("java", "springBoot", "jenkins"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "JDK 8 → 21 upgrade across 14 Spring Boot services",
                                            "languageMigration",
                                            List.of("java", "springBoot"))),
                            "Kickoff with the Acme platform team. Agreed to pilot Devin on the JDK upgrade across 14 Spring Boot services."),
                    new HistorySpec(
                            "mem_priya",
                            "cli_acme",
                            "team_acme_platform",
                            -35,
                            10,
                            75,
                            "sprintInADay",
                            "Acme Platform — Sprint in a day",
                            Map.of(
                                    "jane.doe@acmefinancial.com",
                                    "productOwner",
                                    "raj.patel@acmefinancial.com",
                                    "architect"),
                            List.of(
                                    "lena.fischer@acmefinancial.com",
                                    "omar.haddad@acmefinancial.com",
                                    "yuki.tanaka@acmefinancial.com"),
                            10,
                            List.of("java", "springBoot", "jenkins"),
                            "prRaised",
                            List.of(
                                    new UseCaseSpec(
                                            "Upgrade payments-ledger and fx-rates services to Java 21",
                                            "languageMigration",
                                            List.of("java", "springBoot")),
                                    new UseCaseSpec(
                                            "Jenkins pipeline credentials for Devin",
                                            "devops",
                                            List.of("jenkins"))),
                            "Upgraded two services live. Jenkins pipeline credentials blocked the third; follow-up scheduled."),
                    new HistorySpec(
                            "mem_priya",
                            "cli_acme",
                            "team_acme_platform",
                            -34,
                            10,
                            75,
                            "201",
                            "Acme Platform — 201 session: playbooks",
                            Map.of(
                                    "jane.doe@acmefinancial.com",
                                    "productOwner",
                                    "raj.patel@acmefinancial.com",
                                    "architect"),
                            List.of(
                                    "lena.fischer@acmefinancial.com",
                                    "omar.haddad@acmefinancial.com",
                                    "yuki.tanaka@acmefinancial.com"),
                            10,
                            List.of("java", "springBoot"),
                            "playbookCreated",
                            List.of(
                                    new UseCaseSpec(
                                            "Shared playbook for the remaining service upgrades",
                                            "languageMigration",
                                            List.of("java", "springBoot"))),
                            "Finished the remaining services with a shared playbook. Team now self-serving."),
                    new HistorySpec(
                            "mem_marcus",
                            "cli_acme",
                            "team_acme_payments",
                            -21,
                            14,
                            90,
                            "101",
                            "Acme Payments — Devin 101 session",
                            Map.of(
                                    "chris.lowe@acmefinancial.com",
                                    "engineeringManager",
                                    "raj.patel@acmefinancial.com",
                                    "architect"),
                            List.of("yuki.tanaka@acmefinancial.com"),
                            8,
                            List.of("kotlin", "kafka", "githubActions"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "Devin Review on Kafka consumer PRs",
                                            "devops",
                                            List.of("kotlin", "kafka", "githubActions"))),
                            "Payments team wants Devin Review on all Kafka consumer PRs first."),
                    new HistorySpec(
                            "mem_marcus",
                            "cli_acme",
                            "team_acme_payments",
                            -14,
                            14,
                            60,
                            "ama",
                            "Acme Payments — Ask me anything",
                            Map.of("chris.lowe@acmefinancial.com", "engineeringManager"),
                            List.of("yuki.tanaka@acmefinancial.com"),
                            5,
                            List.of("kotlin"),
                            "devinSession",
                            List.of(
                                    new UseCaseSpec(
                                            "Tune Devin Review rules for consumer repos",
                                            "devops",
                                            List.of("kotlin"))),
                            "Tuned Devin Review rules; three PRs reviewed live."),
                    new HistorySpec(
                            "mem_sofia",
                            "cli_northwind",
                            "team_nw_fulfilment",
                            -28,
                            9,
                            90,
                            "101",
                            "Northwind WMS — Devin 101 session",
                            Map.of(
                                    "maya.okafor@northwind.io",
                                    "productOwner",
                                    "ben.carter@northwind.io",
                                    "architect"),
                            List.of("ingrid.solberg@northwind.io", "diego.ruiz@northwind.io"),
                            15,
                            List.of("dotnet", "azure", "gitlabCi"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "Architecture documentation of the WMS monolith",
                                            "platformMigration",
                                            List.of("dotnet"))),
                            "Northwind wants to decompose the WMS monolith; first ask is architecture documentation."),
                    new HistorySpec(
                            "mem_sofia",
                            "cli_northwind",
                            "team_nw_fulfilment",
                            -20,
                            9,
                            120,
                            "pairProgramming",
                            "Northwind WMS — Pair programming: documentation",
                            Map.of(
                                    "maya.okafor@northwind.io",
                                    "productOwner",
                                    "ben.carter@northwind.io",
                                    "architect"),
                            List.of("ingrid.solberg@northwind.io", "diego.ruiz@northwind.io"),
                            12,
                            List.of("dotnet", "gitlabCi"),
                            "prRaised",
                            List.of(
                                    new UseCaseSpec(
                                            "Module docs for 6 bounded contexts",
                                            "platformMigration",
                                            List.of("dotnet")),
                                    new UseCaseSpec(
                                            "GitLab CI changes for the extracted services",
                                            "devops",
                                            List.of("gitlabCi"))),
                            "Generated module docs for 6 bounded contexts; team adopted DeepWiki for onboarding."),
                    new HistorySpec(
                            "mem_alex",
                            "cli_helios",
                            "team_helios_grid",
                            -24,
                            13,
                            90,
                            "101",
                            "Helios Grid — Devin 101 session",
                            Map.of(
                                    "sam.whitaker@helios-energy.com",
                                    "projectManager",
                                    "aisha.bello@helios-energy.com",
                                    "architect"),
                            List.of("noah.kim@helios-energy.com"),
                            9,
                            List.of("python", "go", "kubernetes", "terraform"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "Restricted-network rollout plan for grid services",
                                            "platformMigration",
                                            List.of("kubernetes", "terraform"))),
                            "VDI-only environment; agreed on a restricted-network rollout plan before hands-on."),
                    new HistorySpec(
                            "mem_priya",
                            "cli_meridian",
                            "team_mer_clinical",
                            -31,
                            11,
                            90,
                            "101",
                            "Meridian Clinical — Devin 101 session",
                            Map.of(
                                    "grace.liu@meridianhealth.org",
                                    "productOwner",
                                    "victor.mensah@meridianhealth.org",
                                    "engineeringManager"),
                            List.of(
                                    "hannah.weiss@meridianhealth.org",
                                    "luis.ortega@meridianhealth.org"),
                            14,
                            List.of("angular", "typescript", "nodejs"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "AngularJS → Angular migration of the scheduling app",
                                            "languageMigration",
                                            List.of("angular", "typescript"))),
                            "Clinical Apps team targets the AngularJS → Angular migration of the scheduling app."),
                    new HistorySpec(
                            "mem_priya",
                            "cli_meridian",
                            "team_mer_clinical",
                            -17,
                            11,
                            75,
                            "pairProgramming",
                            "Meridian Clinical — Pair programming: Angular migration",
                            Map.of("grace.liu@meridianhealth.org", "productOwner"),
                            List.of(
                                    "hannah.weiss@meridianhealth.org",
                                    "luis.ortega@meridianhealth.org"),
                            11,
                            List.of("angular", "typescript"),
                            "prRaised",
                            List.of(
                                    new UseCaseSpec(
                                            "Migrate 4 scheduling components to Angular",
                                            "languageMigration",
                                            List.of("angular", "typescript"))),
                            "Migrated 4 of 9 components; SSO for Bitbucket blocked the rest for half the room."),
                    new HistorySpec(
                            "mem_marcus",
                            "cli_meridian",
                            "team_mer_data",
                            -10,
                            15,
                            90,
                            "101",
                            "Meridian Data Platform — Devin 101 session",
                            Map.of("victor.mensah@meridianhealth.org", "engineeringManager"),
                            List.of("luis.ortega@meridianhealth.org"),
                            7,
                            List.of("python", "postgres", "gcp", "jira"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "Jira bug triage for the ingestion pipelines",
                                            "etlMigration",
                                            List.of("python", "postgres", "jira"))),
                            "Data platform wants Devin on Jira bug triage for the ingestion pipelines."),
                    new HistorySpec(
                            "mem_sofia",
                            "cli_orbital",
                            "team_orb_storefront",
                            -13,
                            10,
                            90,
                            "101",
                            "Orbital Storefront — Devin 101 session",
                            Map.of("pavel.novak@orbitalretail.com", "productOwner"),
                            List.of("emma.clarke@orbitalretail.com"),
                            10,
                            List.of("react", "typescript", "nodejs"),
                            "devinSession",
                            List.of(
                                    new UseCaseSpec(
                                            "Promo banner feature via Slack-triggered Devin",
                                            "devops",
                                            List.of("react", "typescript"))),
                            "Storefront team wants Slack-triggered feature work; first ask is a promo banner feature."),
                    new HistorySpec(
                            "mem_sofia",
                            "cli_orbital",
                            "team_orb_storefront",
                            -6,
                            10,
                            60,
                            "ama",
                            "Orbital Storefront — Ask me anything",
                            Map.of("pavel.novak@orbitalretail.com", "productOwner"),
                            List.of("emma.clarke@orbitalretail.com"),
                            4,
                            List.of("react"),
                            "prRaised",
                            List.of(
                                    new UseCaseSpec(
                                            "Two storefront features shipped through Slack",
                                            "devops",
                                            List.of("react"))),
                            "Pilot approved for Q4. Two features shipped by Devin via Slack."),
                    new HistorySpec(
                            "mem_alex",
                            "cli_vantage",
                            "team_van_claims",
                            -9,
                            9,
                            90,
                            "101",
                            "Vantage Claims — Devin 101 session",
                            Map.of(
                                    "nadia.rahman@vantage-ins.com",
                                    "executiveSponsor",
                                    "felix.brandt@vantage-ins.com",
                                    "architect",
                                    "zoe.martin@vantage-ins.com",
                                    "projectManager"),
                            List.of(),
                            18,
                            List.of("java", "oracle", "cobol", "jenkins"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "COBOL claims engine documentation",
                                            "languageMigration",
                                            List.of("cobol", "java"))),
                            "Executive sponsor present; COBOL-to-Java claims engine documentation is phase one."),
                    new HistorySpec(
                            "mem_sofia",
                            "cli_mercedes",
                            "team_mb_data",
                            -12,
                            10,
                            90,
                            "101",
                            "Mercedes-Benz Data Engineering — Devin 101 session",
                            Map.of(
                                    "katrin.vogel@mercedes-benz.com",
                                    "productOwner",
                                    "jonas.keller@mercedes-benz.com",
                                    "architect"),
                            List.of(
                                    "ravi.menon@mercedes-benz.com",
                                    "lukas.brandt@mercedes-benz.com"),
                            14,
                            List.of("abInitio", "python", "databricks", "excel"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "Ab Initio graphs → PySpark on Databricks",
                                            "etlMigration",
                                            List.of("abInitio", "python", "databricks")),
                                    new UseCaseSpec(
                                            "Fleet KPI spreadsheets → Power BI",
                                            "excelToDashboard",
                                            List.of("excel", "powerBi"))),
                            "Data Engineering wants Devin on the Ab Initio retirement; Excel KPI packs are the quick win."),
                    new HistorySpec(
                            "mem_marcus",
                            "cli_helios",
                            "team_helios_grid",
                            -3,
                            13,
                            75,
                            "pairProgramming",
                            "Helios Grid — Pair programming (restricted network)",
                            Map.of(
                                    "aisha.bello@helios-energy.com",
                                    "architect",
                                    "sam.whitaker@helios-energy.com",
                                    "projectManager"),
                            List.of("noah.kim@helios-energy.com"),
                            8,
                            List.of("python", "kubernetes"),
                            "analysisDone",
                            List.of(
                                    new UseCaseSpec(
                                            "Unit test generation for grid services (blocked by egress)",
                                            "platformMigration",
                                            List.of("python", "kubernetes"))),
                            "Outbound egress still blocked from VDI; session converted to a planning discussion."));

    private void seedHistory(Instant now) {
        LocalDate anchor =
                LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int i = 0;
        for (var h : HISTORY) {
            String id = "ses_hist_" + (++i);
            ZonedDateTime start =
                    anchor.plusDays(h.dayOffset()).atTime(LocalTime.of(h.hour(), 0)).atZone(ZONE);
            ZonedDateTime end = start.plusMinutes(h.minutes());
            Instant created = end.plusMinutes(20).toInstant();
            String provenance =
                    "{\"title\":\"accepted\",\"clientId\":\"accepted\",\"teamId\":\"accepted\",\"facilitatorId\":\"accepted\","
                            + "\"contacts\":\"accepted\",\"participantIds\":\"accepted\",\"participantsEstimated\":\"accepted\","
                            + "\"sessionType\":\"accepted\",\"technologyIds\":\"accepted\",\"summary\":\"manual\",\"outcomeId\":\"manual\",\"useCases\":\"manual\"}";
            jdbc.sql(
                            """
                    insert into sessions (id, organization_id, state, title, client_id, team_id, facilitator_id, scheduled_start, scheduled_end,
                                          actual_start, actual_end, participants_estimated, session_type_id, outcome_id, summary, notes, provenance,
                                          version, created_by, created_at, updated_by, updated_at)
                    values (:id, :org, 'completed', :title, :client, :team, :by, :start, :end, :start, :end, :est, :type, :outcome, :summary, '', :prov,
                            1, :by, :created, :by, :created)
                    """)
                    .param("id", id)
                    .param("org", ORG)
                    .param("title", h.title())
                    .param("client", h.client())
                    .param("team", h.team())
                    .param("by", h.by())
                    .param("start", start.toInstant())
                    .param("end", end.toInstant())
                    .param("est", h.estimated())
                    .param("type", h.type())
                    .param("outcome", h.outcome())
                    .param("summary", h.summary())
                    .param("prov", provenance)
                    .param("created", created)
                    .update();
            h.contacts()
                    .forEach(
                            (email, role) ->
                                    jdbc.sql(
                                                    "insert into session_contacts (session_id, person_id, role_id) values (:s, :p, :r)")
                                            .param("s", id)
                                            .param("p", personId(email))
                                            .param("r", role)
                                            .update());
            for (String email : h.participants()) {
                jdbc.sql("insert into session_participants (session_id, person_id) values (:s, :p)")
                        .param("s", id)
                        .param("p", personId(email))
                        .update();
            }
            for (String tech : h.tech()) {
                jdbc.sql(
                                "insert into session_technologies (session_id, technology_id) values (:s, :t)")
                        .param("s", id)
                        .param("t", tech)
                        .update();
            }
            int order = 0;
            for (var uc : h.useCases()) {
                String ucId = id + "_uc" + (++order);
                jdbc.sql(
                                "insert into use_cases (id, session_id, title, description, use_case_type_id, sort_order) values (:id, :s, :title, '', :type, :o)")
                        .param("id", ucId)
                        .param("s", id)
                        .param("title", uc.title())
                        .param("type", uc.type())
                        .param("o", order)
                        .update();
                for (String tech : uc.tech()) {
                    jdbc.sql(
                                    "insert into use_case_technologies (use_case_id, technology_id) values (:u, :t)")
                            .param("u", ucId)
                            .param("t", tech)
                            .update();
                }
            }
        }
    }
}
