-- Which runtime track (prod or sandbox) owns this database. Claimed by the first API start and
-- checked on every later start, so a developer/mock runtime can never open the production data.
CREATE TABLE deployment_track (
    id          SMALLINT     NOT NULL PRIMARY KEY CHECK (id = 1),
    track       VARCHAR(16)  NOT NULL,
    claimed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
