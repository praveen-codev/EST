import { addDays, addMinutes, setHours, setMinutes, startOfWeek } from "date-fns";
import { stableId } from "@/lib/domain/ids";
import type {
  Client,
  ContactRole,
  Member,
  Person,
  Session,
  SessionType,
  Tag,
  Team,
} from "@/lib/domain/types";

export const ORG_DOMAIN = "cognition.ai";

/* ───────────────────────── Members (personas) ───────────────────────── */

const memberSpecs = [
  ["Alex Rivera", "alex@cognition.ai", "Applied AI Engineer", "MEMBER", 262],
  ["Priya Nair", "priya@cognition.ai", "Applied AI Engineer", "MEMBER", 330],
  ["Marcus Chen", "marcus@cognition.ai", "Applied AI Engineer", "MEMBER", 160],
  ["Sofia Alvarez", "sofia@cognition.ai", "Senior Applied AI Engineer", "MEMBER", 25],
  ["Dana Whitfield", "dana@cognition.ai", "Head of Enablement", "MANAGER", 200],
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

/* ───────────────────────── Taxonomy ───────────────────────── */

const tagSpecs: Array<[string, Tag["category"], string[]]> = [
  ["Java", "technology", ["java"]],
  ["Spring Boot", "technology", ["springboot", "spring", "spring-boot"]],
  ["Kotlin", "technology", ["kotlin"]],
  ["Python", "technology", ["python", "py"]],
  ["TypeScript", "technology", ["typescript", "ts"]],
  ["React", "technology", ["react", "reactjs", "react.js"]],
  ["Angular", "technology", ["angular", "angularjs"]],
  ["Node.js", "technology", ["node", "nodejs", "node.js"]],
  [".NET", "technology", [".net", "dotnet", "c#", "csharp"]],
  ["Go", "technology", ["golang"]],
  ["Kubernetes", "technology", ["k8s", "kubernetes"]],
  ["Terraform", "technology", ["terraform", "tf"]],
  ["AWS", "technology", ["aws", "amazon web services"]],
  ["GCP", "technology", ["gcp", "google cloud"]],
  ["Azure", "technology", ["azure"]],
  ["PostgreSQL", "technology", ["postgres", "postgresql"]],
  ["Oracle DB", "technology", ["oracle", "oracle db", "pl/sql", "plsql"]],
  ["Kafka", "technology", ["kafka"]],
  ["GitHub", "workflow", ["github", "gh"]],
  ["GitLab", "workflow", ["gitlab"]],
  ["Bitbucket", "workflow", ["bitbucket"]],
  ["Jenkins", "workflow", ["jenkins"]],
  ["Jira", "workflow", ["jira"]],
  ["Java 8 → 21 upgrade", "migration", ["java upgrade", "jdk upgrade", "java 8", "java 17", "java 21"]],
  ["Spring Boot 2 → 3", "migration", ["spring boot 3", "boot 3", "spring upgrade"]],
  ["AngularJS → Angular", "migration", ["angularjs migration", "angular upgrade"]],
  ["Monolith → microservices", "migration", ["microservices", "monolith", "decomposition"]],
  ["COBOL modernization", "migration", ["cobol", "mainframe"]],
  ["Unit test generation", "useCase", ["unit tests", "test generation", "test coverage", "tests"]],
  ["Code review automation", "useCase", ["code review", "pr review"]],
  ["Bug triage", "useCase", ["bug triage", "triage", "bugs"]],
  ["Documentation generation", "useCase", ["docs", "documentation", "readme"]],
  ["Dependency upgrades", "useCase", ["dependency upgrade", "dependabot", "cve", "vulnerabilities"]],
  ["Feature implementation", "useCase", ["feature", "implementation"]],
  ["Codebase Q&A", "useCase", ["q&a", "onboarding questions", "deepwiki"]],
  ["Devin Playbooks", "workflow", ["playbook", "playbooks"]],
  ["Devin Review", "workflow", ["devin review"]],
  ["Slack integration", "workflow", ["slack"]],
  ["Air-gapped / VDI", "blocker", ["air-gapped", "airgapped", "vdi", "no wifi"]],
  ["SSO / access pending", "blocker", ["sso", "access pending", "okta"]],
  ["Pilot approved", "outcome", ["pilot", "pilot approved"]],
  ["Expansion opportunity", "outcome", ["expansion", "upsell"]],
];

export const TAGS: Tag[] = tagSpecs.map(([label, category, aliases]) => ({
  id: stableId("tag", label),
  label,
  category,
  aliases: [label.toLowerCase(), ...aliases.map((a) => a.toLowerCase())],
}));

export const tagByLabel = (label: string) => TAGS.find((t) => t.label === label)!;

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
  tags: string[];
  ask: Session["devinAsk"];
  outcome: Session["automationOutcome"];
  success: Session["successState"];
  summary: string;
}

const H: HistorySpec[] = [
  {
    by: "priya@cognition.ai", client: "Acme Financial", team: "Platform Engineering", dayOffset: -42, hour: 10, duration: 90,
    type: "kickoff", title: "Acme Platform — Devin kickoff",
    contacts: [["jane.doe@acmefinancial.com", "productOwner"], ["raj.patel@acmefinancial.com", "architect"], ["tom.becker@acmefinancial.com", "projectManager"]],
    participants: ["lena.fischer@acmefinancial.com", "omar.haddad@acmefinancial.com"], estimated: 12,
    tags: ["Java", "Spring Boot", "Jenkins", "Java 8 → 21 upgrade", "Codebase Q&A"], ask: "plan", outcome: "notApplicable", success: "successful",
    summary: "Kickoff with the Acme platform team. Agreed to pilot Devin on the JDK upgrade across 14 Spring Boot services.",
  },
  {
    by: "priya@cognition.ai", client: "Acme Financial", team: "Platform Engineering", dayOffset: -35, hour: 10, duration: 75,
    type: "day1of2", title: "Acme Platform — Hands-on workshop Day 1",
    contacts: [["jane.doe@acmefinancial.com", "productOwner"], ["raj.patel@acmefinancial.com", "architect"]],
    participants: ["lena.fischer@acmefinancial.com", "omar.haddad@acmefinancial.com", "yuki.tanaka@acmefinancial.com"], estimated: 10,
    tags: ["Java", "Spring Boot", "Jenkins", "Java 8 → 21 upgrade", "Unit test generation"], ask: "implementation", outcome: "partial", success: "partial",
    summary: "Upgraded two services live. Jenkins pipeline credentials blocked the third; follow-up scheduled.",
  },
  {
    by: "priya@cognition.ai", client: "Acme Financial", team: "Platform Engineering", dayOffset: -34, hour: 10, duration: 75,
    type: "day2of2", title: "Acme Platform — Hands-on workshop Day 2",
    contacts: [["jane.doe@acmefinancial.com", "productOwner"], ["raj.patel@acmefinancial.com", "architect"]],
    participants: ["lena.fischer@acmefinancial.com", "omar.haddad@acmefinancial.com", "yuki.tanaka@acmefinancial.com"], estimated: 10,
    tags: ["Java", "Spring Boot", "Java 8 → 21 upgrade", "Unit test generation", "Devin Playbooks"], ask: "implementation", outcome: "completed", success: "successful",
    summary: "Finished the remaining services with a shared playbook. Team now self-serving.",
  },
  {
    by: "marcus@cognition.ai", client: "Acme Financial", team: "Payments", dayOffset: -21, hour: 14, duration: 90,
    type: "kickoff", title: "Acme Payments — Devin kickoff",
    contacts: [["chris.lowe@acmefinancial.com", "engineeringManager"], ["raj.patel@acmefinancial.com", "architect"]],
    participants: ["yuki.tanaka@acmefinancial.com"], estimated: 8,
    tags: ["Kotlin", "Kafka", "Code review automation", "GitHub"], ask: "plan", outcome: "notApplicable", success: "successful",
    summary: "Payments team wants Devin Review on all Kafka consumer PRs first.",
  },
  {
    by: "marcus@cognition.ai", client: "Acme Financial", team: "Payments", dayOffset: -14, hour: 14, duration: 60,
    type: "officeHours", title: "Acme Payments — office hours",
    contacts: [["chris.lowe@acmefinancial.com", "engineeringManager"]],
    participants: ["yuki.tanaka@acmefinancial.com"], estimated: 5,
    tags: ["Kotlin", "Code review automation", "Devin Review"], ask: "supportRequest", outcome: "completed", success: "successful",
    summary: "Tuned Devin Review rules; three PRs reviewed live.",
  },
  {
    by: "sofia@cognition.ai", client: "Northwind Logistics", team: "Fulfilment Engineering", dayOffset: -28, hour: 9, duration: 90,
    type: "kickoff", title: "Northwind WMS — enablement kickoff",
    contacts: [["maya.okafor@northwind.io", "productOwner"], ["ben.carter@northwind.io", "architect"]],
    participants: ["ingrid.solberg@northwind.io", "diego.ruiz@northwind.io"], estimated: 15,
    tags: [".NET", "Azure", "GitLab", "Monolith → microservices", "Documentation generation"], ask: "investigation", outcome: "notApplicable", success: "successful",
    summary: "Northwind wants to decompose the WMS monolith; first ask is architecture documentation.",
  },
  {
    by: "sofia@cognition.ai", client: "Northwind Logistics", team: "Fulfilment Engineering", dayOffset: -20, hour: 9, duration: 120,
    type: "handsOnWorkshop", title: "Northwind WMS — hands-on: documentation sprint",
    contacts: [["maya.okafor@northwind.io", "productOwner"], ["ben.carter@northwind.io", "architect"]],
    participants: ["ingrid.solberg@northwind.io", "diego.ruiz@northwind.io"], estimated: 12,
    tags: [".NET", "GitLab", "Documentation generation", "Codebase Q&A"], ask: "implementation", outcome: "completed", success: "successful",
    summary: "Generated module docs for 6 bounded contexts; team adopted DeepWiki for onboarding.",
  },
  {
    by: "alex@cognition.ai", client: "Helios Energy", team: "Grid Software", dayOffset: -24, hour: 13, duration: 90,
    type: "kickoff", title: "Helios Grid — Devin kickoff",
    contacts: [["sam.whitaker@helios-energy.com", "projectManager"], ["aisha.bello@helios-energy.com", "architect"]],
    participants: ["noah.kim@helios-energy.com"], estimated: 9,
    tags: ["Python", "Go", "Kubernetes", "Terraform", "Unit test generation", "Air-gapped / VDI"], ask: "plan", outcome: "notApplicable", success: "partial",
    summary: "VDI-only environment; agreed on a restricted-network rollout plan before hands-on.",
  },
  {
    by: "priya@cognition.ai", client: "Meridian Health", team: "Clinical Apps", dayOffset: -31, hour: 11, duration: 90,
    type: "kickoff", title: "Meridian Clinical — kickoff",
    contacts: [["grace.liu@meridianhealth.org", "productOwner"], ["victor.mensah@meridianhealth.org", "engineeringManager"]],
    participants: ["hannah.weiss@meridianhealth.org", "luis.ortega@meridianhealth.org"], estimated: 14,
    tags: ["Angular", "TypeScript", "Node.js", "AngularJS → Angular", "Bitbucket"], ask: "plan", outcome: "notApplicable", success: "successful",
    summary: "Clinical Apps team targets the AngularJS → Angular migration of the scheduling app.",
  },
  {
    by: "priya@cognition.ai", client: "Meridian Health", team: "Clinical Apps", dayOffset: -17, hour: 11, duration: 75,
    type: "handsOnWorkshop", title: "Meridian Clinical — Angular migration hands-on",
    contacts: [["grace.liu@meridianhealth.org", "productOwner"]],
    participants: ["hannah.weiss@meridianhealth.org", "luis.ortega@meridianhealth.org"], estimated: 11,
    tags: ["Angular", "TypeScript", "AngularJS → Angular", "Unit test generation", "Bitbucket"], ask: "implementation", outcome: "partial", success: "partial",
    summary: "Migrated 4 of 9 components; SSO for Bitbucket blocked the rest for half the room.",
  },
  {
    by: "marcus@cognition.ai", client: "Meridian Health", team: "Data Platform", dayOffset: -10, hour: 15, duration: 90,
    type: "kickoff", title: "Meridian Data Platform — kickoff",
    contacts: [["victor.mensah@meridianhealth.org", "engineeringManager"]],
    participants: ["luis.ortega@meridianhealth.org"], estimated: 7,
    tags: ["Python", "PostgreSQL", "GCP", "Bug triage", "Jira"], ask: "investigation", outcome: "notApplicable", success: "successful",
    summary: "Data platform wants Devin on Jira bug triage for the ingestion pipelines.",
  },
  {
    by: "sofia@cognition.ai", client: "Orbital Retail", team: "Storefront", dayOffset: -13, hour: 10, duration: 90,
    type: "kickoff", title: "Orbital Storefront — Devin kickoff",
    contacts: [["pavel.novak@orbitalretail.com", "productOwner"]],
    participants: ["emma.clarke@orbitalretail.com"], estimated: 10,
    tags: ["React", "TypeScript", "Node.js", "GitHub", "Feature implementation", "Slack integration"], ask: "implementation", outcome: "notApplicable", success: "successful",
    summary: "Storefront team wants Slack-triggered feature work; first ask is a promo banner feature.",
  },
  {
    by: "sofia@cognition.ai", client: "Orbital Retail", team: "Storefront", dayOffset: -6, hour: 10, duration: 60,
    type: "officeHours", title: "Orbital Storefront — office hours",
    contacts: [["pavel.novak@orbitalretail.com", "productOwner"]],
    participants: ["emma.clarke@orbitalretail.com"], estimated: 4,
    tags: ["React", "Slack integration", "Feature implementation", "Pilot approved"], ask: "supportRequest", outcome: "completed", success: "successful",
    summary: "Pilot approved for Q4. Two features shipped by Devin via Slack.",
  },
  {
    by: "alex@cognition.ai", client: "Vantage Insurance", team: "Claims Modernization", dayOffset: -9, hour: 9, duration: 90,
    type: "kickoff", title: "Vantage Claims — modernization kickoff",
    contacts: [["nadia.rahman@vantage-ins.com", "executiveSponsor"], ["felix.brandt@vantage-ins.com", "architect"], ["zoe.martin@vantage-ins.com", "projectManager"]],
    participants: [], estimated: 18,
    tags: ["Java", "Oracle DB", "COBOL modernization", "Jenkins", "Documentation generation"], ask: "plan", outcome: "notApplicable", success: "successful",
    summary: "Executive sponsor present; COBOL-to-Java claims engine documentation is phase one.",
  },
  {
    by: "marcus@cognition.ai", client: "Helios Energy", team: "Grid Software", dayOffset: -3, hour: 13, duration: 75,
    type: "handsOnWorkshop", title: "Helios Grid — restricted-network hands-on",
    contacts: [["aisha.bello@helios-energy.com", "architect"], ["sam.whitaker@helios-energy.com", "projectManager"]],
    participants: ["noah.kim@helios-energy.com"], estimated: 8,
    tags: ["Python", "Kubernetes", "Unit test generation", "Air-gapped / VDI"], ask: "implementation", outcome: "blocked", success: "unsuccessful",
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
      "participantsEstimated", "sessionType", "successState", "automationOutcome", "devinAsk", "tagIds", "summary",
    ] as const) {
      provenance[f] = "accepted";
    }
    provenance.summary = "manual";
    provenance.successState = "manual";
    provenance.automationOutcome = "manual";
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
      successState: h.success,
      automationOutcome: h.outcome,
      devinAsk: h.ask,
      tagIds: h.tags.map((t) => tagByLabel(t).id),
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
