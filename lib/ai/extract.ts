import { stableId } from "@/lib/domain/ids";
import { CONTACT_ROLE_LABELS, DEVIN_ASK_LABELS, SESSION_TYPE_LABELS } from "@/lib/domain/labels";
import type {
  Client,
  ContactRole,
  DevinAsk,
  Evidence,
  ID,
  Member,
  Person,
  Session,
  SessionContact,
  SessionType,
  Suggestion,
  SuggestionField,
  Tag,
  Team,
} from "@/lib/domain/types";
import type { NormalizedEvent } from "./normalize";

export const PROMPT_VERSION = "sim-ai/1.0.0";

export interface EngineContext {
  members: Member[];
  clients: Client[];
  teams: Team[];
  people: Person[];
  tags: Tag[];
  sessions: Session[];
  ownerMemberId: ID;
}

export type DraftSuggestion = Pick<Suggestion, "field" | "value" | "label" | "confidence" | "evidence">;

export interface ClientCreate {
  create: { name: string; domain: string };
}

export interface ExtractionResult {
  suggestions: DraftSuggestion[];
  /** External attendees not yet known as people; created alongside the candidate. */
  newPeople: Person[];
  matchedClient: Client | null;
}

/** Fields whose suggestions are individual items appended to a list on accept. */
export const ITEM_FIELDS: ReadonlySet<SuggestionField> = new Set(["coFacilitatorIds", "contacts", "tagIds"]);

/** Role prefix (case-insensitive) followed by a capitalised full name (case-sensitive). */
const ROLE_PREFIXES: Array<[RegExp, ContactRole]> = [
  [/\b(?:po|product owner)\b\s*[:\-–]?\s*/gi, "productOwner"],
  [/\b(?:pm|project manager)\b\s*[:\-–]?\s*/gi, "projectManager"],
  [/\barchitect\b\s*[:\-–]?\s*/gi, "architect"],
  [/\b(?:em|eng(?:ineering)? manager)\b\s*[:\-–]?\s*/gi, "engineeringManager"],
  [/\b(?:exec(?:utive)? )?sponsor\b\s*[:\-–]?\s*/gi, "executiveSponsor"],
];
const CAPITALISED_NAME = /^([A-Z][\w'-]+(?:\s+[A-Z][\w'-]+){1,2})/;

function* roleMentions(description: string): Generator<{ name: string; role: ContactRole; excerpt: string }> {
  for (const [re, role] of ROLE_PREFIXES) {
    for (const m of description.matchAll(re)) {
      const rest = description.slice(m.index! + m[0].length);
      const name = rest.match(CAPITALISED_NAME)?.[1];
      if (name) yield { name: name.trim(), role, excerpt: `${m[0].trim()} ${name.trim()}` };
    }
  }
}

const TYPE_PATTERNS: Array<[RegExp, SessionType, number]> = [
  [/\bday\s*1\b|\bday one\b/i, "day1of2", 0.95],
  [/\bday\s*2\b|\bday two\b/i, "day2of2", 0.95],
  [/\bkick-?off\b/i, "kickoff", 0.92],
  [/\boffice hours\b/i, "officeHours", 0.95],
  [/\bfollow[- ]?up\b/i, "followUp", 0.9],
  [/\btraining\b|\bnew joiners\b|\bonboarding\b/i, "training", 0.9],
  [/\bworkshop\b|\bhands[- ]on\b/i, "handsOnWorkshop", 0.9],
];

const ASK_KEYWORDS: Record<DevinAsk, string[]> = {
  plan: ["kickoff", "kick-off", "plan", "roadmap", "intro", "scoping"],
  supportRequest: ["office hours", "support", "q&a", "troubleshoot", "follow-up", "follow up", "stragglers"],
  implementation: ["hands-on", "hands on", "workshop", "upgrade", "migrat", "generat", "rollout", "roll out", "implement", "extract", "finish"],
  investigation: ["investigat", "assess", "evaluat", "readout", "triage", "review results"],
};

function nameFromEmail(email: string): string {
  const local = email.split("@")[0] ?? "";
  return local
    .split(/[._-]/)
    .filter(Boolean)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

function companyFromDomain(domain: string): string {
  const root = domain.split(".")[0] ?? domain;
  return root
    .replace(/sw$/, " Software")
    .replace(/-/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function containsTerm(text: string, term: string): boolean {
  const t = term.toLowerCase();
  const pattern = /^[a-z0-9]/.test(t) && /[a-z0-9]$/.test(t) ? `\\b${escapeRegExp(t)}\\b` : escapeRegExp(t);
  return new RegExp(pattern, "i").test(text);
}

export function extract(n: NormalizedEvent, ctx: EngineContext): ExtractionResult {
  const out: DraftSuggestion[] = [];
  const add = (field: SuggestionField, value: unknown, label: string, confidence: number, evidence: Evidence[]) =>
    out.push({ field, value, label, confidence: Number(confidence.toFixed(2)), evidence });

  const memberByEmail = new Map(ctx.members.map((m) => [m.email.toLowerCase(), m]));
  const personByEmail = new Map(ctx.people.filter((p) => p.email).map((p) => [p.email!.toLowerCase(), p]));
  const memberName = (id: ID) => ctx.members.find((m) => m.id === id)?.name ?? "a teammate";

  /* ── Client ── */
  let client: Client | null = null;
  const domainHits = new Map<string, number>();
  for (const a of n.external) domainHits.set(a.domain, (domainHits.get(a.domain) ?? 0) + 1);
  const rankedDomains = [...domainHits.entries()].sort((a, b) => b[1] - a[1]);
  for (const [domain, count] of rankedDomains) {
    const c = ctx.clients.find((cl) => cl.domains.includes(domain));
    if (c) {
      client = c;
      const history = ctx.sessions.filter((s) => s.clientId === c.id);
      const byOthers = history.filter((s) => s.facilitatorId !== ctx.ownerMemberId);
      const ev: Evidence[] = [
        { source: "calendar.attendees", excerpt: `${count} attendee${count > 1 ? "s" : ""} @${domain}` },
      ];
      if (history.length) {
        const names = [...new Set(byOthers.map((s) => memberName(s.facilitatorId!)))].slice(0, 2);
        ev.push({
          source: "history.client",
          excerpt: `${history.length} prior session${history.length > 1 ? "s" : ""} with ${c.name}`,
          attribution: names.length ? `logged by ${names.join(" & ")}` : undefined,
        });
      }
      add("clientId", c.id, c.name, 0.96, ev);
      break;
    }
  }
  if (!client) {
    const byName = ctx.clients.find((c) => containsTerm(n.text, c.name.split(" ")[0]!));
    if (byName) {
      client = byName;
      add("clientId", byName.id, byName.name, 0.86, [{ source: "calendar.title", excerpt: n.title }]);
    } else if (rankedDomains[0]) {
      const domain = rankedDomains[0][0];
      const titleMatch = n.title.match(/^(?:intro(?:\s+call)?:\s*)?([A-Z][\w&]+(?:\s+[A-Z][\w&]+)*)/);
      const name = titleMatch?.[1] && !/^(workshop|training|intro|devin)$/i.test(titleMatch[1]) ? titleMatch[1] : companyFromDomain(domain);
      const value: ClientCreate = { create: { name, domain } };
      add("clientId", value, `New client: ${name}`, 0.62, [
        { source: "calendar.attendees", excerpt: `Unknown domain @${domain}` },
        { source: "calendar.title", excerpt: n.title },
      ]);
    }
  }

  /* ── Team ── */
  if (client) {
    const teams = ctx.teams.filter((t) => t.clientId === client!.id);
    const kw = teams.find((t) => t.keywords.some((k) => containsTerm(n.text, k)) || containsTerm(n.text, t.name));
    if (kw) {
      const hit = kw.keywords.find((k) => containsTerm(n.text, k)) ?? kw.name;
      add("teamId", kw.id, kw.name, 0.9, [{ source: "calendar.title", excerpt: `"${hit}" in the invite` }]);
    } else if (teams.length === 1) {
      add("teamId", teams[0]!.id, teams[0]!.name, 0.8, [
        { source: "history.team", excerpt: `Only team recorded for ${client.name}` },
      ]);
    } else if (teams.length > 1) {
      const counts = new Map<ID, number>();
      for (const s of ctx.sessions) if (s.clientId === client.id && s.teamId) counts.set(s.teamId, (counts.get(s.teamId) ?? 0) + 1);
      const top = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
      if (top) {
        const t = teams.find((x) => x.id === top[0])!;
        add("teamId", t.id, t.name, 0.55, [
          { source: "history.team", excerpt: `${top[1]} of ${ctx.sessions.filter((s) => s.clientId === client!.id).length} ${client.name} sessions were with ${t.name}` },
        ]);
      }
    }
  }

  /* ── Title ── */
  const cleanTitle = n.title.replace(/\s*\((?:copy|fwd|forwarded)[^)]*\)\s*/i, "").trim();
  if (cleanTitle) add("title", cleanTitle, cleanTitle, 0.95, [{ source: "calendar.title", excerpt: n.title }]);

  /* ── Session type ── */
  let typeMatched = false;
  for (const [re, type, conf] of TYPE_PATTERNS) {
    const m = `${n.title}\n${n.description}`.match(re);
    if (m) {
      add("sessionType", type, SESSION_TYPE_LABELS[type], conf, [
        { source: re.test(n.title) ? "calendar.title" : "calendar.description", excerpt: `"${m[0]}"` },
      ]);
      typeMatched = true;
      break;
    }
  }
  if (!typeMatched && n.external.length) {
    const fallback: SessionType = n.durationMin >= 90 ? "handsOnWorkshop" : "followUp";
    add("sessionType", fallback, SESSION_TYPE_LABELS[fallback], 0.45, [
      { source: "calendar.time", excerpt: `${n.durationMin} min slot` },
    ]);
  }

  /* ── Facilitators ── */
  const internalMembers = n.internal.map((a) => memberByEmail.get(a.email)).filter((m): m is Member => !!m);
  const organizerMember = n.organizer?.isInternal ? memberByEmail.get(n.organizer.email) : undefined;
  const owner = ctx.members.find((m) => m.id === ctx.ownerMemberId)!;
  const facilitator = organizerMember ?? (internalMembers.find((m) => m.id === owner.id) ? owner : internalMembers[0] ?? owner);
  add("facilitatorId", facilitator.id, facilitator.name, organizerMember ? 0.95 : 0.9, [
    organizerMember
      ? { source: "calendar.organizer", excerpt: `${organizerMember.name} organised the event` }
      : { source: "calendar.attendees", excerpt: `${facilitator.name} is on the invite` },
  ]);
  for (const m of internalMembers) {
    if (m.id === facilitator.id || m.role === "MANAGER") continue;
    add("coFacilitatorIds", m.id, m.name, 0.85, [{ source: "calendar.attendees", excerpt: `${m.name} is on the invite` }]);
  }

  /* ── People: create unknown external attendees ── */
  const newPeople: Person[] = [];
  const resolvePerson = (email: string, name: string | null): Person => {
    const existing = personByEmail.get(email);
    if (existing) return existing;
    const p: Person = {
      id: stableId("per", email),
      clientId: client?.id ?? null,
      name: name ?? nameFromEmail(email),
      email,
      role: null,
    };
    personByEmail.set(email, p);
    newPeople.push(p);
    return p;
  };
  const externalPeople = n.external.map((a) => resolvePerson(a.email, a.name));

  /* ── Contacts ── */
  const seenContacts = new Map<string, DraftSuggestion>();
  const addContact = (person: Person, role: ContactRole, confidence: number, evidence: Evidence[]) => {
    const key = `${person.id}:${role}`;
    const existing = seenContacts.get(key);
    if (existing) {
      // Same contact seen from a second source: corroborate rather than duplicate.
      existing.evidence = [...existing.evidence, ...evidence];
      existing.confidence = Number(Math.min(0.98, existing.confidence + 0.03).toFixed(2));
      return;
    }
    const value: SessionContact = { personId: person.id, role };
    add("contacts", value, `${person.name} · ${CONTACT_ROLE_LABELS[role]}`, confidence, evidence);
    seenContacts.set(key, out[out.length - 1]!);
  };
  for (const { name, role, excerpt } of roleMentions(n.description)) {
    const person =
      externalPeople.find((p) => p.name.toLowerCase() === name.toLowerCase()) ??
      [...personByEmail.values()].find((p) => p.name.toLowerCase() === name.toLowerCase() && (!client || p.clientId === client.id));
    if (person) addContact(person, role, 0.92, [{ source: "calendar.description", excerpt: `"${excerpt}"` }]);
  }
  if (client) {
    const roleStats = new Map<string, { count: number; by: Set<ID> }>();
    for (const s of ctx.sessions) {
      if (s.clientId !== client.id) continue;
      for (const c of s.contacts) {
        const key = `${c.personId}:${c.role}`;
        const entry = roleStats.get(key) ?? { count: 0, by: new Set<ID>() };
        entry.count += 1;
        if (s.facilitatorId) entry.by.add(s.facilitatorId);
        roleStats.set(key, entry);
      }
    }
    for (const p of externalPeople) {
      for (const [key, stat] of roleStats) {
        const [personId, role] = key.split(":") as [ID, ContactRole];
        if (personId !== p.id) continue;
        const names = [...stat.by].map(memberName);
        addContact(p, role, Math.min(0.9, 0.7 + 0.07 * stat.count), [
          {
            source: "history.people",
            excerpt: `Recorded as ${CONTACT_ROLE_LABELS[role]} on ${stat.count} ${client.name} session${stat.count > 1 ? "s" : ""}`,
            attribution: `by ${names.slice(0, 2).join(" & ")}`,
          },
        ]);
      }
    }
  }

  /* ── Participants ── */
  if (externalPeople.length) {
    add(
      "participantIds",
      externalPeople.map((p) => p.id),
      `${externalPeople.length} attendee${externalPeople.length > 1 ? "s" : ""}${client ? ` from ${client.name}` : ""}`,
      0.9,
      [{ source: "calendar.attendees", excerpt: externalPeople.slice(0, 4).map((p) => p.name).join(", ") + (externalPeople.length > 4 ? "…" : "") }],
    );
  }
  const est = n.description.match(/(?:~|approx\.?|about|around)?\s*(\d{1,3})\s+(?:new\s+)?(?:engineers|developers|devs|people|attendees|participants|data engineers)/i);
  if (est) {
    add("participantsEstimated", Number(est[1]), `~${est[1]} people`, 0.88, [{ source: "calendar.description", excerpt: `"${est[0].trim()}"` }]);
  } else if (externalPeople.length >= 3) {
    add("participantsEstimated", externalPeople.length, `${externalPeople.length} (from invite)`, 0.5, [
      { source: "calendar.attendees", excerpt: `${externalPeople.length} external attendees accepted` },
    ]);
  }

  /* ── Devin ask ── */
  const askScores = (Object.keys(ASK_KEYWORDS) as DevinAsk[]).map((ask) => {
    const hits = ASK_KEYWORDS[ask].filter((k) => n.text.includes(k));
    return { ask, hits };
  });
  const bestAsk = askScores.sort((a, b) => b.hits.length - a.hits.length)[0];
  if (bestAsk && bestAsk.hits.length) {
    add("devinAsk", bestAsk.ask, DEVIN_ASK_LABELS[bestAsk.ask], Math.min(0.88, 0.6 + 0.12 * bestAsk.hits.length), [
      { source: n.title.toLowerCase().includes(bestAsk.hits[0]!) ? "calendar.title" : "calendar.description", excerpt: `"${bestAsk.hits.slice(0, 2).join('", "')}"` },
    ]);
  }

  /* ── Tags ── */
  const matchedTagIds = new Set<ID>();
  for (const tag of ctx.tags) {
    const alias = tag.aliases.find((a) => containsTerm(n.text, a));
    if (!alias) continue;
    matchedTagIds.add(tag.id);
    add("tagIds", tag.id, tag.label, alias === tag.label.toLowerCase() ? 0.92 : 0.88, [
      { source: alias === tag.label.toLowerCase() ? "calendar.description" : "taxonomy.alias", excerpt: alias === tag.label.toLowerCase() ? `"${tag.label}" in the invite` : `"${alias}" → ${tag.label}` },
    ]);
  }
  if (client) {
    const scope = ctx.sessions.filter((s) => s.clientId === client!.id);
    const tagStats = new Map<ID, { count: number; by: Set<ID> }>();
    for (const s of scope) {
      for (const t of s.tagIds) {
        const e = tagStats.get(t) ?? { count: 0, by: new Set<ID>() };
        e.count += 1;
        if (s.facilitatorId) e.by.add(s.facilitatorId);
        tagStats.set(t, e);
      }
    }
    for (const [tagId, stat] of [...tagStats.entries()].sort((a, b) => b[1].count - a[1].count)) {
      if (matchedTagIds.has(tagId) || stat.count < 2) continue;
      const tag = ctx.tags.find((t) => t.id === tagId)!;
      const names = [...stat.by].map(memberName);
      add("tagIds", tag.id, tag.label, Math.min(0.75, 0.45 + 0.1 * stat.count), [
        {
          source: "history.tags",
          excerpt: `Used on ${stat.count} of ${scope.length} ${client.name} sessions`,
          attribution: `by ${names.slice(0, 2).join(" & ")}`,
        },
      ]);
    }
  }

  /* ── Draft summary ── */
  if (n.description && n.external.length) {
    const firstSentence = n.description.split(/(?<=[.!?])\s+|\n/)[0]!.replace(/\[link\]|\[phone\]/g, "").trim();
    if (firstSentence.length > 12) {
      const draft = `${client ? `${client.name}: ` : ""}${firstSentence}`;
      add("summary", draft, "Draft summary from the invite", 0.55, [{ source: "calendar.description", excerpt: `"${firstSentence}"` }]);
    }
  }

  return { suggestions: out, newPeople, matchedClient: client };
}
