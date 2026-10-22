--liquibase formatted sql

-- Per-board fetch state of the source adapters: the validator (`etag`) of the last accepted list fetch and the start of
-- the last run, so politeness rules survive restarts. Holds no posting text and no personal data.
--changeset moldo:007-source-fetch-state
CREATE TABLE source_fetch_state
(
    source_id   TEXT                     NOT NULL PRIMARY KEY,
    etag        TEXT,
    last_run_at TIMESTAMP WITH TIME ZONE NOT NULL
);
--rollback DROP TABLE source_fetch_state;
