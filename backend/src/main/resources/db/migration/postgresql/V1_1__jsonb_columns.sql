-- PostgreSQL only: promote JSON-bearing TEXT columns to jsonb for indexing and validation.
-- Defaults must be dropped before the type change and re-added as jsonb literals.
ALTER TABLE sessions ALTER COLUMN provenance DROP DEFAULT;
ALTER TABLE sessions ALTER COLUMN provenance TYPE jsonb USING provenance::jsonb;
ALTER TABLE sessions ALTER COLUMN provenance SET DEFAULT '{}'::jsonb;

ALTER TABLE candidates ALTER COLUMN reasons DROP DEFAULT;
ALTER TABLE candidates ALTER COLUMN reasons TYPE jsonb USING reasons::jsonb;
ALTER TABLE candidates ALTER COLUMN reasons SET DEFAULT '[]'::jsonb;
ALTER TABLE candidates ALTER COLUMN redactions DROP DEFAULT;
ALTER TABLE candidates ALTER COLUMN redactions TYPE jsonb USING redactions::jsonb;
ALTER TABLE candidates ALTER COLUMN redactions SET DEFAULT '[]'::jsonb;

ALTER TABLE suggestions ALTER COLUMN value_json TYPE jsonb USING value_json::jsonb;
ALTER TABLE suggestions ALTER COLUMN evidence DROP DEFAULT;
ALTER TABLE suggestions ALTER COLUMN evidence TYPE jsonb USING evidence::jsonb;
ALTER TABLE suggestions ALTER COLUMN evidence SET DEFAULT '[]'::jsonb;

ALTER TABLE calendar_events ALTER COLUMN raw TYPE jsonb USING raw::jsonb;

ALTER TABLE audit_events ALTER COLUMN details DROP DEFAULT;
ALTER TABLE audit_events ALTER COLUMN details TYPE jsonb USING details::jsonb;
ALTER TABLE audit_events ALTER COLUMN details SET DEFAULT '{}'::jsonb;

CREATE INDEX ix_suggestions_value_gin ON suggestions USING gin (value_json);
