--liquibase formatted sql

-- Canonical, normalized job postings, shared by all users (no owner column on purpose). `raw_payload` is the source's
-- own text, kept unchanged for re-parsing. `dedupe_key` is null when the city is unknown; it is deliberately not unique
-- because a company may repost the same ad.
--changeset moldo:005-posting
CREATE TABLE posting
(
    id           UUID                     NOT NULL PRIMARY KEY,
    title        TEXT                     NOT NULL,
    company      TEXT                     NOT NULL,
    description  TEXT                     NOT NULL,
    city         TEXT,
    canton       TEXT,
    workload_min INT,
    workload_max INT,
    remote_mode  TEXT,
    salary_min   INT,
    salary_max   INT,
    language     TEXT                     NOT NULL,
    out_of_scope BOOLEAN                  NOT NULL,
    needs_review BOOLEAN                  NOT NULL,
    dedupe_key   TEXT,
    content_hash TEXT                     NOT NULL,
    raw_payload  TEXT                     NOT NULL,
    first_seen   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen    TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX posting_dedupe_key_idx ON posting (dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX posting_first_seen_idx ON posting (first_seen DESC);
--rollback DROP TABLE posting;

-- One row per (source, source's own id). Several sources can point at one canonical posting; only the primary one
-- (the source that created it) may change the canonical fields.
--changeset moldo:006-posting-source
CREATE TABLE posting_source
(
    id                UUID                     NOT NULL PRIMARY KEY,
    posting_id        UUID                     NOT NULL REFERENCES posting (id) ON DELETE CASCADE,
    source_id         TEXT                     NOT NULL,
    source_posting_id TEXT                     NOT NULL,
    url               TEXT                     NOT NULL,
    is_primary        BOOLEAN                  NOT NULL,
    last_seen         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT posting_source_unique UNIQUE (source_id, source_posting_id)
);
CREATE INDEX posting_source_posting_idx ON posting_source (posting_id);
--rollback DROP TABLE posting_source;
