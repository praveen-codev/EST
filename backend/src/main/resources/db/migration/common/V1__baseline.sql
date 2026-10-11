-- Enablement Session Tracker — baseline schema (portable: H2 in PostgreSQL mode and PostgreSQL 17).
-- Conventions: string ids (VARCHAR 64), UTC timestamps, organization_id on every business table,
-- JSON kept in TEXT here (upgraded to jsonb by the postgresql-specific migration), keywords as a
-- '|'-joined TEXT list, audit columns created_at/updated_at/updated_by, version for optimistic locking.

---------------------------------------------------------------------------------------------------
-- Identity
---------------------------------------------------------------------------------------------------
CREATE TABLE organizations (
    id              VARCHAR(64)  PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    allowed_domains TEXT         NOT NULL DEFAULT '',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE members (
    id              VARCHAR(64)  PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    google_sub      VARCHAR(128),
    email           VARCHAR(320) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    title           VARCHAR(200),
    role            VARCHAR(16)  NOT NULL,          -- MEMBER | MANAGER | ADMIN
    status          VARCHAR(16)  NOT NULL,          -- PENDING | ACTIVE | SUSPENDED
    hue             INTEGER      NOT NULL DEFAULT 220,
    time_zone       VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    last_sign_in_at TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_members_email UNIQUE (organization_id, email)
);
CREATE UNIQUE INDEX uq_members_google_sub ON members (google_sub);

-- Google (or other) accounts linked to a member for calendar access. Several per member.
CREATE TABLE member_identities (
    id                  VARCHAR(64)  PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    member_id           VARCHAR(64)  NOT NULL REFERENCES members(id),
    provider            VARCHAR(32)  NOT NULL,       -- google | mock
    provider_sub        VARCHAR(128),
    email               VARCHAR(320) NOT NULL,
    display_name        VARCHAR(200),
    scopes              TEXT         NOT NULL DEFAULT '',
    refresh_token_enc   TEXT,                        -- AES-256-GCM ciphertext, base64
    refresh_token_iv    VARCHAR(64),
    sync_token          TEXT,                        -- Google incremental sync cursor
    status              VARCHAR(16)  NOT NULL,       -- ACTIVE | NEEDS_REAUTH | DISABLED
    last_sync_at        TIMESTAMP WITH TIME ZONE,
    last_error          TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_identity_member_email UNIQUE (member_id, provider, email)
);

-- Rotating refresh tokens: only hashes are stored. A family is revoked as a whole on reuse or sign-out.
CREATE TABLE refresh_token_families (
    id                  VARCHAR(64)  PRIMARY KEY,
    member_id           VARCHAR(64)  NOT NULL REFERENCES members(id),
    current_hash        VARCHAR(128) NOT NULL,
    previous_hash       VARCHAR(128),
    rotation_count      INTEGER      NOT NULL DEFAULT 0,
    user_agent_hash     VARCHAR(128),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at          TIMESTAMP WITH TIME ZONE,
    reuse_detected_at   TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX uq_rtf_current_hash ON refresh_token_families (current_hash);
CREATE INDEX ix_rtf_member ON refresh_token_families (member_id);

-- Short-lived state for the calendar-linking authorization-code flow (PKCE verifier kept encrypted).
CREATE TABLE oauth_link_states (
    state_hash          VARCHAR(128) PRIMARY KEY,
    member_id           VARCHAR(64)  NOT NULL REFERENCES members(id),
    pkce_verifier_enc   TEXT         NOT NULL,
    pkce_verifier_iv    VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

---------------------------------------------------------------------------------------------------
-- Reference tables (admin-managed catalogues)
---------------------------------------------------------------------------------------------------
CREATE TABLE catalog_items (
    id              VARCHAR(64)  NOT NULL,
    organization_id VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    kind            VARCHAR(32)  NOT NULL,           -- sessionType | sessionOutcome | technology | useCaseType | contactRole
    label           VARCHAR(200) NOT NULL,
    description     TEXT         NOT NULL DEFAULT '',
    keywords        TEXT         NOT NULL DEFAULT '', -- '|'-joined, lower-case
    typical_minutes INTEGER,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    version         INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by      VARCHAR(64),
    PRIMARY KEY (organization_id, id)
);
CREATE INDEX ix_catalog_kind ON catalog_items (organization_id, kind, archived, sort_order);

---------------------------------------------------------------------------------------------------
-- Enablement domain
---------------------------------------------------------------------------------------------------
CREATE TABLE clients (
    id              VARCHAR(64)  PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    name            VARCHAR(200) NOT NULL,
    industry        VARCHAR(120),
    hue             INTEGER      NOT NULL DEFAULT 220,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by      VARCHAR(64)
);
CREATE INDEX ix_clients_org ON clients (organization_id, name);

CREATE TABLE client_domains (
    client_id       VARCHAR(64)  NOT NULL REFERENCES clients(id),
    domain          VARCHAR(255) NOT NULL,
    PRIMARY KEY (client_id, domain)
);
CREATE INDEX ix_client_domains_domain ON client_domains (domain);

CREATE TABLE teams (
    id              VARCHAR(64)  PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    client_id       VARCHAR(64)  NOT NULL REFERENCES clients(id),
    name            VARCHAR(200) NOT NULL,
    keywords        TEXT         NOT NULL DEFAULT '',
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_teams_client ON teams (client_id);

CREATE TABLE people (
    id              VARCHAR(64)  PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    client_id       VARCHAR(64)  REFERENCES clients(id),
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(320),
    role_id         VARCHAR(64),                      -- contactRole catalogue id, last known
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_people_org_email ON people (organization_id, email);
CREATE INDEX ix_people_client ON people (client_id);

CREATE TABLE sessions (
    id                      VARCHAR(64)  PRIMARY KEY,
    organization_id         VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    state                   VARCHAR(16)  NOT NULL,   -- planned | completed | incomplete | cancelled | ignored
    title                   VARCHAR(500) NOT NULL,
    client_id               VARCHAR(64)  REFERENCES clients(id),
    team_id                 VARCHAR(64)  REFERENCES teams(id),
    facilitator_id          VARCHAR(64)  REFERENCES members(id),
    scheduled_start         TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_end           TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_start            TIMESTAMP WITH TIME ZONE,
    actual_end              TIMESTAMP WITH TIME ZONE,
    participants_estimated  INTEGER,
    session_type_id         VARCHAR(64),
    outcome_id              VARCHAR(64),
    summary                 TEXT         NOT NULL DEFAULT '',
    notes                   TEXT         NOT NULL DEFAULT '',
    provenance              TEXT         NOT NULL DEFAULT '{}',  -- JSON: field -> prefilled|accepted|manual
    version                 INTEGER      NOT NULL DEFAULT 1,
    created_by              VARCHAR(64)  NOT NULL REFERENCES members(id),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by              VARCHAR(64)  NOT NULL REFERENCES members(id),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_sessions_org_start ON sessions (organization_id, scheduled_start);
CREATE INDEX ix_sessions_client ON sessions (client_id, scheduled_start);
CREATE INDEX ix_sessions_facilitator ON sessions (facilitator_id, scheduled_start);

CREATE TABLE session_co_facilitators (
    session_id  VARCHAR(64) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    member_id   VARCHAR(64) NOT NULL REFERENCES members(id),
    PRIMARY KEY (session_id, member_id)
);

CREATE TABLE session_contacts (
    session_id  VARCHAR(64) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    person_id   VARCHAR(64) NOT NULL REFERENCES people(id),
    role_id     VARCHAR(64) NOT NULL,                 -- contactRole catalogue id
    PRIMARY KEY (session_id, person_id, role_id)
);

CREATE TABLE session_participants (
    session_id  VARCHAR(64) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    person_id   VARCHAR(64) NOT NULL REFERENCES people(id),
    PRIMARY KEY (session_id, person_id)
);

CREATE TABLE session_technologies (
    session_id      VARCHAR(64) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    technology_id   VARCHAR(64) NOT NULL,             -- technology catalogue id
    PRIMARY KEY (session_id, technology_id)
);

CREATE TABLE use_cases (
    id                  VARCHAR(64)  PRIMARY KEY,
    session_id          VARCHAR(64)  NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    title               VARCHAR(500) NOT NULL DEFAULT '',
    description         TEXT         NOT NULL DEFAULT '',
    use_case_type_id    VARCHAR(64),
    sort_order          INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX ix_use_cases_session ON use_cases (session_id, sort_order);

CREATE TABLE use_case_technologies (
    use_case_id     VARCHAR(64) NOT NULL REFERENCES use_cases(id) ON DELETE CASCADE,
    technology_id   VARCHAR(64) NOT NULL,
    PRIMARY KEY (use_case_id, technology_id)
);

---------------------------------------------------------------------------------------------------
-- Calendar ingestion (evidence layer — written only by sync)
---------------------------------------------------------------------------------------------------
CREATE TABLE calendar_sync_runs (
    id                  VARCHAR(64)  PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    identity_id         VARCHAR(64)  NOT NULL REFERENCES member_identities(id),
    kind                VARCHAR(16)  NOT NULL,       -- initial | incremental | manual | full_resync
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at         TIMESTAMP WITH TIME ZONE,
    status              VARCHAR(16)  NOT NULL,       -- running | ok | failed
    events_seen         INTEGER      NOT NULL DEFAULT 0,
    events_new          INTEGER      NOT NULL DEFAULT 0,
    events_updated      INTEGER      NOT NULL DEFAULT 0,
    events_unchanged    INTEGER      NOT NULL DEFAULT 0,
    candidates_created  INTEGER      NOT NULL DEFAULT 0,
    error               TEXT
);
CREATE INDEX ix_sync_runs_identity ON calendar_sync_runs (identity_id, started_at);

CREATE TABLE calendar_events (
    id                  VARCHAR(64)  PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    identity_id         VARCHAR(64)  NOT NULL REFERENCES member_identities(id),
    owner_member_id     VARCHAR(64)  NOT NULL REFERENCES members(id),
    provider_event_id   VARCHAR(255) NOT NULL,
    recurring_event_id  VARCHAR(255),
    title               VARCHAR(1000) NOT NULL DEFAULT '',
    description         TEXT         NOT NULL DEFAULT '',
    location            VARCHAR(1000),
    conferencing        VARCHAR(1000),
    start_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    time_zone           VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    organizer_email     VARCHAR(320),
    etag                VARCHAR(255),
    content_hash        VARCHAR(64)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,       -- confirmed | cancelled
    raw                 TEXT,                        -- provider payload (JSON) for audit/replay
    first_seen_run_id   VARCHAR(64)  REFERENCES calendar_sync_runs(id),
    last_seen_run_id    VARCHAR(64)  REFERENCES calendar_sync_runs(id),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_event_identity_provider UNIQUE (identity_id, provider_event_id)
);
CREATE INDEX ix_events_owner_start ON calendar_events (owner_member_id, start_at);
CREATE INDEX ix_events_hash ON calendar_events (owner_member_id, content_hash);

CREATE TABLE calendar_event_attendees (
    event_id        VARCHAR(64)  NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
    email           VARCHAR(320) NOT NULL,
    display_name    VARCHAR(200),
    response_status VARCHAR(16)  NOT NULL DEFAULT 'needsAction',
    organizer       BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (event_id, email)
);

---------------------------------------------------------------------------------------------------
-- AI proposals (proposal layer — needs a human decision)
---------------------------------------------------------------------------------------------------
CREATE TABLE candidates (
    id                          VARCHAR(64)  PRIMARY KEY,
    organization_id             VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    event_id                    VARCHAR(64)  NOT NULL REFERENCES calendar_events(id),
    owner_member_id             VARCHAR(64)  NOT NULL REFERENCES members(id),
    score                       NUMERIC(4,2) NOT NULL,
    bucket                      VARCHAR(16)  NOT NULL,   -- likely | review | unlikely
    reasons                     TEXT         NOT NULL DEFAULT '[]',   -- JSON array of strings
    duplicate_of_candidate_id   VARCHAR(64),
    status                      VARCHAR(16)  NOT NULL,   -- open | confirmed | dismissed | merged
    session_id                  VARCHAR(64)  REFERENCES sessions(id),
    redacted_text               TEXT         NOT NULL DEFAULT '',
    redactions                  TEXT         NOT NULL DEFAULT '[]',   -- JSON array of strings
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_candidate_event UNIQUE (event_id)
);
CREATE INDEX ix_candidates_owner_status ON candidates (owner_member_id, status, bucket);

CREATE TABLE suggestions (
    id              VARCHAR(64)  PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    candidate_id    VARCHAR(64)  NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    session_id      VARCHAR(64)  REFERENCES sessions(id),
    field           VARCHAR(40)  NOT NULL,
    value_json      TEXT         NOT NULL,              -- JSON
    label           VARCHAR(500) NOT NULL,
    confidence      NUMERIC(4,2) NOT NULL,
    evidence        TEXT         NOT NULL DEFAULT '[]', -- JSON array of {source, excerpt, attribution}
    status          VARCHAR(16)  NOT NULL,              -- proposed | accepted | rejected | edited | superseded
    prompt_version  VARCHAR(40)  NOT NULL,
    decided_by      VARCHAR(64)  REFERENCES members(id),
    decided_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_suggestions_candidate ON suggestions (candidate_id, status);
CREATE INDEX ix_suggestions_session ON suggestions (session_id, status);

CREATE TABLE session_source_events (
    session_id  VARCHAR(64) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    event_id    VARCHAR(64) NOT NULL REFERENCES calendar_events(id),
    PRIMARY KEY (session_id, event_id)
);

---------------------------------------------------------------------------------------------------
-- Governance
---------------------------------------------------------------------------------------------------
CREATE TABLE audit_events (
    id              VARCHAR(64)  PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL REFERENCES organizations(id),
    actor_member_id VARCHAR(64),
    action          VARCHAR(64)  NOT NULL,              -- member.approved, identity.linked, suggestion.accepted, ...
    target_type     VARCHAR(40)  NOT NULL,
    target_id       VARCHAR(64),
    details         TEXT         NOT NULL DEFAULT '{}', -- JSON, never contains credentials
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_audit_org_time ON audit_events (organization_id, occurred_at);
