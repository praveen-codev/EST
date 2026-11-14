CREATE TABLE archive_runs (
    id                  VARCHAR(64) PRIMARY KEY,
    organization_id     VARCHAR(64) NOT NULL,
    source_project      VARCHAR(64) NOT NULL,
    cutoff_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at         TIMESTAMP WITH TIME ZONE,
    status              VARCHAR(16) NOT NULL,
    evidence_seen       INTEGER NOT NULL DEFAULT 0,
    evidence_written    INTEGER NOT NULL DEFAULT 0,
    bytes_written       BIGINT NOT NULL DEFAULT 0,
    error               TEXT
);

CREATE INDEX ix_archive_runs_org_started
    ON archive_runs (organization_id, started_at DESC);

CREATE TABLE archived_calendar_evidence (
    organization_id     VARCHAR(64) NOT NULL,
    event_id            VARCHAR(64) NOT NULL,
    owner_member_id     VARCHAR(64) NOT NULL,
    source_identity_id  VARCHAR(64) NOT NULL,
    provider_event_id   VARCHAR(255) NOT NULL,
    occurred_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    schema_version      INTEGER NOT NULL,
    payload_json        JSONB NOT NULL,
    payload_checksum    VARCHAR(64) NOT NULL,
    source_updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    archive_run_id      VARCHAR(64) NOT NULL REFERENCES archive_runs(id),
    PRIMARY KEY (organization_id, event_id, schema_version)
);

CREATE INDEX ix_archive_evidence_org_time
    ON archived_calendar_evidence (organization_id, occurred_at DESC);

CREATE INDEX ix_archive_evidence_provider
    ON archived_calendar_evidence (organization_id, source_identity_id, provider_event_id);
