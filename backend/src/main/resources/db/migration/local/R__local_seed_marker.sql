-- Local profile only. The rich demo data set (personas, clients, people, historical sessions with
-- use cases) is inserted by com.cognition.est.app.dev.LocalSeedRunner so that dates stay relative to
-- the day the API starts. This repeatable migration just records that the local location is active.
CREATE TABLE IF NOT EXISTS local_seed_marker (applied_at TIMESTAMP WITH TIME ZONE NOT NULL);
