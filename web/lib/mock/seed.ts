import { addDays, addMinutes, setHours, setMinutes, startOfWeek } from "date-fns";
import { stableId } from "@/lib/domain/ids";
import type {
  CatalogItem,
  CatalogKind,
  Client,
  ContactRole,
  Member,
  Person,
  Session,
  SessionType,
  Team,
  UseCase,
} from "@/lib/domain/types";

export const ORG_DOMAIN = "cognition.ai";

/* ───────────────────────── Members (personas) ───────────────────────── */

const memberSpecs = [
  ["Alex Rivera", "alex@cognition.ai", "Applied AI Engineer", "MEMBER", 262],
  ["Priya Nair", "priya@cognition.ai", "Applied AI Engineer", "MEMBER", 330],
  ["Marcus Chen", "marcus@cognition.ai", "Applied AI Engineer", "MEMBER", 160],
  ["Sofia Alvarez", "sofia@cognition.ai", "Senior Applied AI Engineer", "MEMBER", 25],
  ["Dana Whitfield", "dana@cognition.ai", "Head of Enablement", "ADMIN", 200],
] as const;

export const MEMBERS: Member[] = memberSpecs.map(([name, email, title, role, hue]) => ({
  id: stableId("mem", email),
  name,
  email,
  title,
  role,
  hue,
}));

export const DEFAULT_MEMBER_ID = MEMBERS[0]!.id;
export const memberByEmail = (email: string) =>
  MEMBERS.find((m) => m.email.toLowerCase() === email.toLowerCase()) ?? null;

/* ───────────────────────── Clients & teams ───────────────────────── */

const clientSpecs: Array<[string, string[], string, number, Array<[string, string[]]>]> = [
  [
    "Acme Financial",
    ["acmefinancial.com", "acme-fin.com"],
    "Financial services",
    220,
    [
      ["Platform Engineering", ["platform", "core platform"]],
      ["Payments", ["payments", "pay"]],
    ],
  ],
  [
    "Northwind Logistics",
    ["northwind.io"],
    "Logistics",
    150,
    [["Fulfilment Engineering", ["fulfilment", "fulfillment", "wms"]]],
  ],
  [
    "Helios Energy",
    ["helios-energy.com"],
    "Energy",
    40,
    [["Grid Software", ["grid", "scada"]]],
  ],
  [
    "Meridian Health",
    ["meridianhealth.org"],
    "Healthcare",
    300,
    [
      ["Clinical Apps", ["clinical", "ehr"]],
      ["Data Platform", ["data platform", "analytics", "data eng"]],
    ],
  ],
  ["Orbital Retail", ["orbitalretail.com"], "Retail", 10, [["Storefront", ["storefront", "web"]]]],
  [
    "Vantage Insurance",
    ["vantage-ins.com"],
    "Insurance",
    190,
    [["Claims Modernization", ["claims", "modernization", "modernisation"]]],
  ],
  [
    "Mercedes-Benz Tech",
    ["mercedes-benz.com", "mbtech.io"],
    "Automotive",
    95,
    [
      ["Data Engineering", ["data engineering", "data eng", "ab initio", "etl"]],
      ["Connected Car", ["connected car", "telematics"]],
    ],
  ],
];

export const CLIENTS: Client[] = clientSpecs.map(([name, domains, industry, hue]) => ({
  id: stableId("cli", name),
  name,
  domains,
  industry,
  hue,
}));

export const TEAMS: Team[] = clientSpecs.flatMap(([clientName, , , , teams]) =>
  teams.map(([name, keywords]) => ({
    id: stableId("team", `${clientName}/${name}`),
    clientId: stableId("cli", clientName),
    name,
    keywords,
  })),
);

export const clientByName = (name: string) => CLIENTS.find((c) => c.name === name)!;
export const teamByName = (clientName: string, teamName: string) =>
  TEAMS.find((t) => t.id === stableId("team", `${clientName}/${teamName}`))!;

/* ───────────────────────── People (client contacts) ───────────────────────── */

const peopleSpecs: Array<[string, string, string, ContactRole | null]> = [
  // Acme Financial
  ["Acme Financial", "Jane Doe", "jane.doe@acmefinancial.com", "productOwner"],
  ["Acme Financial", "Raj Patel", "raj.patel@acmefinancial.com", "architect"],
  ["Acme Financial", "Tom Becker", "tom.becker@acmefinancial.com", "projectManager"],
  ["Acme Financial", "Lena Fischer", "lena.fischer@acmefinancial.com", "engineer"],
  ["Acme Financial", "Omar Haddad", "omar.haddad@acmefinancial.com", "engineer"],
  ["Acme Financial", "Yuki Tanaka", "yuki.tanaka@acmefinancial.com", "engineer"],
  ["Acme Financial", "Chris Lowe", "chris.lowe@acmefinancial.com", "engineeringManager"],
  // Northwind
  ["Northwind Logistics", "Maya Okafor", "maya.okafor@northwind.io", "productOwner"],
  ["Northwind Logistics", "Ben Carter", "ben.carter@northwind.io", "architect"],
  ["Northwind Logistics", "Ingrid Solberg", "ingrid.solberg@northwind.io", "engineer"],
  ["Northwind Logistics", "Diego Ruiz", "diego.ruiz@northwind.io", "engineer"],
  // Helios
  ["Helios Energy", "Sam Whitaker", "sam.whitaker@helios-energy.com", "projectManager"],
  ["Helios Energy", "Aisha Bello", "aisha.bello@helios-energy.com", "architect"],
  ["Helios Energy", "Noah Kim", "noah.kim@helios-energy.com", "engineer"],
  // Meridian
  ["Meridian Health", "Grace Liu", "grace.liu@meridianhealth.org", "productOwner"],
  ["Meridian Health", "Victor Mensah", "victor.mensah@meridianhealth.org", "engineeringManager"],
  ["Meridian Health", "Hannah Weiss", "hannah.weiss@meridianhealth.org", "engineer"],
  ["Meridian Health", "Luis Ortega", "luis.ortega@meridianhealth.org", "engineer"],
  // Orbital
  ["Orbital Retail", "Pavel Novak", "pavel.novak@orbitalretail.com", "productOwner"],
  ["Orbital Retail", "Emma Clarke", "emma.clarke@orbitalretail.com", "engineer"],
  // Vantage
  ["Vantage Insurance", "Nadia Rahman", "nadia.rahman@vantage-ins.com", "executiveSponsor"],
  ["Vantage Insurance", "Felix Brandt", "felix.brandt@vantage-ins.com", "architect"],
  ["Vantage Insurance", "Zoe Martin", "zoe.martin@vantage-ins.com", "projectManager"],
  // Mercedes-Benz Tech
  ["Mercedes-Benz Tech", "Katrin Vogel", "katrin.vogel@mercedes-benz.com", "productOwner"],
  ["Mercedes-Benz Tech", "Jonas Keller", "jonas.keller@mercedes-benz.com", "architect"],
  ["Mercedes-Benz Tech", "Anika Schulz", "anika.schulz@mercedes-benz.com", "engineeringManager"],
  ["Mercedes-Benz Tech", "Ravi Menon", "ravi.menon@mercedes-benz.com", "engineer"],
  ["Mercedes-Benz Tech", "Lukas Brandt", "lukas.brandt@mercedes-benz.com", "engineer"],
];

export const PEOPLE: Person[] = peopleSpecs.map(([clientName, name, email, role]) => ({
  id: stableId("per", email),
  clientId: stableId("cli", clientName),
  name,
  email,
  role,
}));

export const personByEmail = (email: string) =>
  PEOPLE.find((p) => p.email?.toLowerCase() === email.toLowerCase()) ?? null;

/* ───────────────────────── Catalogues (admin-managed tables) ───────────────────────── */

type CatalogSpec = [string, string, string[], number | null, string?];

const SESSION_TYPE_SPECS: CatalogSpec[] = [
  ["101", "101 session", ["101", "101 session", "devin 101", "intro session"], 90, "First hands-on introduction to Devin for a new team."],
  ["pairProgramming", "Pair programming", ["pair programming", "pair-programming", "pairing session", "pairing"], 60, "One engineer, one Devin, one real ticket, side by side."],
  ["201", "201 session", ["201", "201 session", "devin 201", "advanced session"], 90, "Advanced follow-on: playbooks, review, integrations."],
  ["ama", "Ask me anything", ["ask me anything", "ama"], 45, "Open Q&A with a team already using Devin."],
  ["sprintInADay", "Sprint in a day", ["sprint in a day", "sprint-in-a-day", "sprint day"], 360, "A full day shipping a backlog slice with Devin."],
];

const SESSION_OUTCOME_SPECS: CatalogSpec[] = [
  ["analysisDone", "Analysis done", [], null, "Devin analysed the codebase or problem; no code change yet."],
  ["devinSession", "Devin session", [], null, "The team ran Devin on real work during the session."],
  ["prRaised", "PR raised", [], null, "At least one pull request came out of the session."],
  ["playbookCreated", "Playbook / automation created", [], null, "A reusable playbook or automation now exists."],
];

const TECHNOLOGY_SPECS: CatalogSpec[] = [
  ["java", "Java", ["java", "jdk", "java 8", "java 17", "java 21"], null],
  ["springBoot", "Spring Boot", ["spring boot", "springboot", "spring"], null],
  ["kotlin", "Kotlin", ["kotlin"], null],
  ["python", "Python", ["python", "py"], null],
  ["typescript", "TypeScript", ["typescript", "ts"], null],
  ["react", "React", ["react", "reactjs"], null],
  ["angular", "Angular", ["angular", "angularjs"], null],
  ["nodejs", "Node.js", ["node", "nodejs", "node.js"], null],
  ["dotnet", ".NET", [".net", "dotnet", "c#", "csharp"], null],
  ["go", "Go", ["golang"], null],
  ["cobol", "COBOL", ["cobol", "mainframe"], null],
  ["abInitio", "Ab Initio", ["ab initio", "abinitio"], null],
  ["informatica", "Informatica", ["informatica", "powercenter"], null],
  ["excel", "Excel", ["excel", "spreadsheet", "xlsx", "vba"], null],
  ["powerBi", "Power BI", ["power bi", "powerbi"], null],
  ["tableau", "Tableau", ["tableau"], null],
  ["sql", "SQL", ["sql", "stored procedures", "pl/sql", "plsql", "t-sql"], null],
  ["oracle", "Oracle DB", ["oracle"], null],
  ["postgres", "PostgreSQL", ["postgres", "postgresql"], null],
  ["snowflake", "Snowflake", ["snowflake"], null],
  ["databricks", "Databricks", ["databricks", "spark", "pyspark"], null],
  ["kafka", "Kafka", ["kafka"], null],
  ["kubernetes", "Kubernetes", ["k8s", "kubernetes"], null],
  ["terraform", "Terraform", ["terraform"], null],
  ["aws", "AWS", ["aws", "amazon web services"], null],
  ["gcp", "GCP", ["gcp", "google cloud"], null],
  ["azure", "Azure", ["azure"], null],
  ["jenkins", "Jenkins", ["jenkins"], null],
  ["githubActions", "GitHub Actions", ["github actions", "gh actions"], null],
  ["gitlabCi", "GitLab CI", ["gitlab ci", "gitlab"], null],
  ["jira", "Jira", ["jira"], null],
];

const USE_CASE_TYPE_SPECS: CatalogSpec[] = [
  ["excelToDashboard", "Excel to dashboard", ["excel to dashboard", "dashboard", "spreadsheet to", "power bi", "tableau", "reporting"], null],
  ["etlMigration", "ETL tool migration", ["etl", "ab initio", "informatica", "datastage", "pipeline migration", "ingestion pipelines"], null],
  ["languageMigration", "Language migrations", ["language migration", "java 21", "jdk upgrade", "cobol", "angularjs", "python 3", "upgrade", "migration"], null],
  ["platformMigration", "Platform migrations", ["platform migration", "cloud migration", "to aws", "to gcp", "to azure", "kubernetes", "microservices", "decomposition"], null],
  ["devops", "Devops work", ["devops", "ci/cd", "pipeline credentials", "jenkins", "github actions", "terraform", "deployment"], null],
];

const CONTACT_ROLE_SPECS: CatalogSpec[] = [
  ["productOwner", "Product owner", ["po", "product owner"], null],
  ["projectManager", "Project manager", ["pm", "project manager"], null],
  ["architect", "Architect", ["architect", "solution architect"], null],
  ["engineeringManager", "Engineering manager", ["em", "eng manager", "engineering manager"], null],
  ["executiveSponsor", "Executive sponsor", ["sponsor", "exec sponsor", "executive sponsor"], null],
  ["engineer", "Engineer", ["engineer", "developer"], null],
];

function catalogRows(kind: CatalogKind, specs: CatalogSpec[]): CatalogItem[] {
  return specs.map(([id, label, keywords, typicalMinutes, description], i) => ({
    id,
    kind,
    label,
    description: description ?? "",
    keywords: [...new Set([label.toLowerCase(), ...keywords.map((k) => k.toLowerCase())])],
    typicalMinutes,
    archived: false,
    sortOrder: i,
    updatedBy: null,
    updatedAt: null,
  }));
}

export const CATALOG: CatalogItem[] = [
  ...catalogRows("sessionType", SESSION_TYPE_SPECS),
  ...catalogRows("sessionOutcome", SESSION_OUTCOME_SPECS),
  ...catalogRows("technology", TECHNOLOGY_SPECS),
  ...catalogRows("useCaseType", USE_CASE_TYPE_SPECS),
  ...catalogRows("contactRole", CONTACT_ROLE_SPECS),
];

export const SESSION_TYPES = CATALOG.filter((c) => c.kind === "sessionType");
export const techByLabel = (label: string) => CATALOG.find((c) => c.kind === "technology" && c.label === label)!;

/* ───────────────────────── Time anchor ───────────────────────── */

/** Monday 00:00 of the current week; all fixtures are relative to it. */
export function anchorDate(now = new Date()): Date {
  return startOfWeek(now, { weekStartsOn: 1 });
}

export function at(anchor: Date, dayOffset: number, hour: number, minute = 0): Date {
  return setMinutes(setHours(addDays(anchor, dayOffset), hour), minute);
}

/* ───────────────────────── Historical sessions (by other engineers) ───────────────────────── */

interface HistorySpec {
  by: string; // member email
  client: string;
  team: string;
  dayOffset: number; // relative to anchor (negative = past weeks)
  hour: number;
  duration: number;
  type: SessionType;
  title: string;
  contacts: Array<[string, ContactRole]>; // person email, role
  participants: string[]; // person emails
  estimated: number | null;
  tech: string[]; // technology labels
  outcome: string; // sessionOutcome id
  useCases: Array<[string, string, string[]]>; // title, useCaseType id, technology labels
  summary: string;
}

const H: HistorySpec[] = [
  {
    by: "priya@cognition.ai", client: "Acme Financial", team: "Platform Engineering", dayOffset: -42, hour: 10, duration: 90,
    type: "101", title: "Acme Platform — Devin 101 session",
    contacts: [["jane.doe@acmefinancial.com", "productOwner"], ["raj.patel@acmefinancial.com", "architect"], ["tom.becker@acmefinancial.com", "projectManager"]],
    participants: ["lena.fischer@acmefinancial.com", "omar.haddad@acmefinancial.com"], estimated: 12,
    tech: ["Java", "Spring Boot", "Jenkins"], outcome: "analysisDone", useCases: [["JDK 8 → 21 upgrade across 14 Spring Boot services", "languageMigration", ["Java", "Spring Boot"]]],
    summary: "Kickoff with the Acme platform team. Agreed to pilot Devin on the JDK upgrade across 14 Spring Boot services.",
  },
  {
    by: "priya@cognition.ai", client: "Acme Financial", team: "Platform Engineering", dayOffset: -35, hour: 10, duration: 75,
    type: "sprintInADay", title: "Acme Platform — Sprint in a day",
    contacts: [["jane.doe@acmefinancial.com", "productOwner"], ["raj.patel@acmefinancial.com", "architect"]],
    participants: ["lena.fischer@acmefinancial.com", "omar.haddad@acmefinancial.com", "yuki.tanaka@acmefinancial.com"], estimated: 10,
    tech: ["Java", "Spring Boot", "Jenkins"], outcome: "prRaised", useCases: [["Upgrade payments-ledger and fx-rates services to Java 21", "languageMigration", ["Java", "Spring Boot"]], ["Jenkins pipeline credentials for Devin", "devops", ["Jenkins"]]],
    summary: "Upgraded two services live. Jenkins pipeline credentials blocked the third; follow-up scheduled.",
  },
  {
    by: "priya@cognition.ai", client: "Acme Financial", team: "Platform Engineering", dayOffset: -34, hour: 10, duration: 75,
    type: "201", title: "Acme Platform — 201 session: playbooks",
    contacts: [["jane.doe@acmefinancial.com", "productOwner"], ["raj.patel@acmefinancial.com", "architect"]],
    participants: ["lena.fischer@acmefinancial.com", "omar.haddad@acmefinancial.com", "yuki.tanaka@acmefinancial.com"], estimated: 10,
    tech: ["Java", "Spring Boot"], outcome: "playbookCreated", useCases: [["Shared playbook for the remaining service upgrades", "languageMigration", ["Java", "Spring Boot"]]],
    summary: "Finished the remaining services with a shared playbook. Team now self-serving.",
  },
  {
    by: "marcus@cognition.ai", client: "Acme Financial", team: "Payments", dayOffset: -21, hour: 14, duration: 90,
    type: "101", title: "Acme Payments — Devin 101 session",
    contacts: [["chris.lowe@acmefinancial.com", "engineeringManager"], ["raj.patel@acmefinancial.com", "architect"]],
    participants: ["yuki.tanaka@acmefinancial.com"], estimated: 8,
    tech: ["Kotlin", "Kafka", "GitHub Actions"], outcome: "analysisDone", useCases: [["Devin Review on Kafka consumer PRs", "devops", ["Kotlin", "Kafka", "GitHub Actions"]]],
    summary: "Payments team wants Devin Review on all Kafka consumer PRs first.",
  },
  {
    by: "marcus@cognition.ai", client: "Acme Financial", team: "Payments", dayOffset: -14, hour: 14, duration: 60,
    type: "ama", title: "Acme Payments — Ask me anything",
    contacts: [["chris.lowe@acmefinancial.com", "engineeringManager"]],
    participants: ["yuki.tanaka@acmefinancial.com"], estimated: 5,
    tech: ["Kotlin"], outcome: "devinSession", useCases: [["Tune Devin Review rules for consumer repos", "devops", ["Kotlin"]]],
    summary: "Tuned Devin Review rules; three PRs reviewed live.",
  },
  {
    by: "sofia@cognition.ai", client: "Northwind Logistics", team: "Fulfilment Engineering", dayOffset: -28, hour: 9, duration: 90,
    type: "101", title: "Northwind WMS — Devin 101 session",
    contacts: [["maya.okafor@northwind.io", "productOwner"], ["ben.carter@northwind.io", "architect"]],
    participants: ["ingrid.solberg@northwind.io", "diego.ruiz@northwind.io"], estimated: 15,
    tech: [".NET", "Azure", "GitLab CI"], outcome: "analysisDone", useCases: [["Architecture documentation of the WMS monolith", "platformMigration", [".NET"]]],
    summary: "Northwind wants to decompose the WMS monolith; first ask is architecture documentation.",
  },
  {
    by: "sofia@cognition.ai", client: "Northwind Logistics", team: "Fulfilment Engineering", dayOffset: -20, hour: 9, duration: 120,
    type: "pairProgramming", title: "Northwind WMS — Pair programming: documentation",
    contacts: [["maya.okafor@northwind.io", "productOwner"], ["ben.carter@northwind.io", "architect"]],
    participants: ["ingrid.solberg@northwind.io", "diego.ruiz@northwind.io"], estimated: 12,
    tech: [".NET", "GitLab CI"], outcome: "prRaised", useCases: [["Module docs for 6 bounded contexts", "platformMigration", [".NET"]], ["GitLab CI changes for the extracted services", "devops", ["GitLab CI"]]],
    summary: "Generated module docs for 6 bounded contexts; team adopted DeepWiki for onboarding.",
  },
  {
    by: "alex@cognition.ai", client: "Helios Energy", team: "Grid Software", dayOffset: -24, hour: 13, duration: 90,
    type: "101", title: "Helios Grid — Devin 101 session",
    contacts: [["sam.whitaker@helios-energy.com", "projectManager"], ["aisha.bello@helios-energy.com", "architect"]],
    participants: ["noah.kim@helios-energy.com"], estimated: 9,
    tech: ["Python", "Go", "Kubernetes", "Terraform"], outcome: "analysisDone", useCases: [["Restricted-network rollout plan for grid services", "platformMigration", ["Kubernetes", "Terraform"]]],
    summary: "VDI-only environment; agreed on a restricted-network rollout plan before hands-on.",
  },
  {
    by: "priya@cognition.ai", client: "Meridian Health", team: "Clinical Apps", dayOffset: -31, hour: 11, duration: 90,
    type: "101", title: "Meridian Clinical — Devin 101 session",
    contacts: [["grace.liu@meridianhealth.org", "productOwner"], ["victor.mensah@meridianhealth.org", "engineeringManager"]],
    participants: ["hannah.weiss@meridianhealth.org", "luis.ortega@meridianhealth.org"], estimated: 14,
    tech: ["Angular", "TypeScript", "Node.js"], outcome: "analysisDone", useCases: [["AngularJS → Angular migration of the scheduling app", "languageMigration", ["Angular", "TypeScript"]]],
    summary: "Clinical Apps team targets the AngularJS → Angular migration of the scheduling app.",
  },
  {
    by: "priya@cognition.ai", client: "Meridian Health", team: "Clinical Apps", dayOffset: -17, hour: 11, duration: 75,
    type: "pairProgramming", title: "Meridian Clinical — Pair programming: Angular migration",
    contacts: [["grace.liu@meridianhealth.org", "productOwner"]],
    participants: ["hannah.weiss@meridianhealth.org", "luis.ortega@meridianhealth.org"], estimated: 11,
    tech: ["Angular", "TypeScript"], outcome: "prRaised", useCases: [["Migrate 4 scheduling components to Angular", "languageMigration", ["Angular", "TypeScript"]]],
    summary: "Migrated 4 of 9 components; SSO for Bitbucket blocked the rest for half the room.",
  },
  {
    by: "marcus@cognition.ai", client: "Meridian Health", team: "Data Platform", dayOffset: -10, hour: 15, duration: 90,
    type: "101", title: "Meridian Data Platform — Devin 101 session",
    contacts: [["victor.mensah@meridianhealth.org", "engineeringManager"]],
    participants: ["luis.ortega@meridianhealth.org"], estimated: 7,
    tech: ["Python", "PostgreSQL", "GCP", "Jira"], outcome: "analysisDone", useCases: [["Jira bug triage for the ingestion pipelines", "etlMigration", ["Python", "PostgreSQL", "Jira"]]],
    summary: "Data platform wants Devin on Jira bug triage for the ingestion pipelines.",
  },
  {
    by: "sofia@cognition.ai", client: "Orbital Retail", team: "Storefront", dayOffset: -13, hour: 10, duration: 90,
    type: "101", title: "Orbital Storefront — Devin 101 session",
    contacts: [["pavel.novak@orbitalretail.com", "productOwner"]],
    participants: ["emma.clarke@orbitalretail.com"], estimated: 10,
    tech: ["React", "TypeScript", "Node.js"], outcome: "devinSession", useCases: [["Promo banner feature via Slack-triggered Devin", "devops", ["React", "TypeScript"]]],
    summary: "Storefront team wants Slack-triggered feature work; first ask is a promo banner feature.",
  },
  {
    by: "sofia@cognition.ai", client: "Orbital Retail", team: "Storefront", dayOffset: -6, hour: 10, duration: 60,
    type: "ama", title: "Orbital Storefront — Ask me anything",
    contacts: [["pavel.novak@orbitalretail.com", "productOwner"]],
    participants: ["emma.clarke@orbitalretail.com"], estimated: 4,
    tech: ["React"], outcome: "prRaised", useCases: [["Two storefront features shipped through Slack", "devops", ["React"]]],
    summary: "Pilot approved for Q4. Two features shipped by Devin via Slack.",
  },
  {
    by: "alex@cognition.ai", client: "Vantage Insurance", team: "Claims Modernization", dayOffset: -9, hour: 9, duration: 90,
    type: "101", title: "Vantage Claims — Devin 101 session",
    contacts: [["nadia.rahman@vantage-ins.com", "executiveSponsor"], ["felix.brandt@vantage-ins.com", "architect"], ["zoe.martin@vantage-ins.com", "projectManager"]],
    participants: [], estimated: 18,
    tech: ["Java", "Oracle DB", "COBOL", "Jenkins"], outcome: "analysisDone", useCases: [["COBOL claims engine documentation", "languageMigration", ["COBOL", "Java"]]],
    summary: "Executive sponsor present; COBOL-to-Java claims engine documentation is phase one.",
  },
  {
    by: "sofia@cognition.ai", client: "Mercedes-Benz Tech", team: "Data Engineering", dayOffset: -12, hour: 10, duration: 90,
    type: "101", title: "Mercedes-Benz Data Engineering — Devin 101 session",
    contacts: [["katrin.vogel@mercedes-benz.com", "productOwner"], ["jonas.keller@mercedes-benz.com", "architect"]],
    participants: ["ravi.menon@mercedes-benz.com", "lukas.brandt@mercedes-benz.com"], estimated: 14,
    tech: ["Ab Initio", "Python", "Databricks", "Excel"], outcome: "analysisDone",
    useCases: [["Ab Initio graphs → PySpark on Databricks", "etlMigration", ["Ab Initio", "Python", "Databricks"]], ["Fleet KPI spreadsheets → Power BI", "excelToDashboard", ["Excel", "Power BI"]]],
    summary: "Data Engineering wants Devin on the Ab Initio retirement; Excel KPI packs are the quick win.",
  },
  {
    by: "marcus@cognition.ai", client: "Helios Energy", team: "Grid Software", dayOffset: -3, hour: 13, duration: 75,
    type: "pairProgramming", title: "Helios Grid — Pair programming (restricted network)",
    contacts: [["aisha.bello@helios-energy.com", "architect"], ["sam.whitaker@helios-energy.com", "projectManager"]],
    participants: ["noah.kim@helios-energy.com"], estimated: 8,
    tech: ["Python", "Kubernetes"], outcome: "analysisDone", useCases: [["Unit test generation for grid services (blocked by egress)", "platformMigration", ["Python", "Kubernetes"]]],
    summary: "Outbound egress still blocked from VDI; session converted to a planning discussion.",
  },
];

export function buildHistoricalSessions(anchor: Date): Session[] {
  return H.map((h) => {
    const member = memberByEmail(h.by)!;
    const client = clientByName(h.client);
    const team = teamByName(h.client, h.team);
    const start = at(anchor, h.dayOffset, h.hour);
    const end = addMinutes(start, h.duration);
    const createdAt = addMinutes(end, 20).toISOString();
    const id = stableId("ses", `${h.by}/${h.client}/${h.dayOffset}/${h.title}`);
    const provenance: Session["provenance"] = {};
    for (const f of [
      "title", "clientId", "teamId", "facilitatorId", "contacts", "participantIds",
      "participantsEstimated", "sessionType", "technologyIds",
    ] as const) {
      provenance[f] = "accepted";
    }
    provenance.summary = "manual";
    provenance.outcomeId = "manual";
    provenance.useCases = "manual";
    const useCases: UseCase[] = h.useCases.map(([title, useCaseTypeId, tech], i) => ({
      id: stableId("uc", `${id}/${i}/${title}`),
      title,
      description: "",
      useCaseTypeId,
      technologyIds: tech.map((t) => techByLabel(t).id),
    }));
    return {
      id,
      state: "completed",
      title: h.title,
      clientId: client.id,
      teamId: team.id,
      facilitatorId: member.id,
      coFacilitatorIds: [],
      contacts: h.contacts.map(([email, role]) => ({ personId: personByEmail(email)!.id, role })),
      scheduledStart: start.toISOString(),
      scheduledEnd: end.toISOString(),
      actualStart: start.toISOString(),
      actualEnd: end.toISOString(),
      participantIds: h.participants.map((e) => personByEmail(e)!.id),
      participantsEstimated: h.estimated,
      sessionType: h.type,
      outcomeId: h.outcome,
      technologyIds: h.tech.map((t) => techByLabel(t).id),
      useCases,
      summary: h.summary,
      notes: "",
      sourceEventIds: [],
      provenance,
      createdBy: member.id,
      createdAt,
      updatedBy: member.id,
      updatedAt: createdAt,
      version: 1,
    };
  });
}
