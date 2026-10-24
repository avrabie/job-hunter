--liquibase formatted sql

-- A user's tracked job: a frozen copy of the posting (title, company, link, plain-text description, time of capture) plus
-- the user's stage and notes. `posting_id` has no foreign key: the posting may disappear and the copy is self-contained.
-- `content_hash` is the live posting's hash at capture time, to tell later whether the live ad changed.
--changeset moldo:016-application
CREATE TABLE application
(
    id                UUID                     NOT NULL PRIMARY KEY,
    owner_sub         TEXT                     NOT NULL REFERENCES app_user (sub) ON DELETE CASCADE,
    posting_id        UUID,
    stage             TEXT                     NOT NULL CHECK (stage IN ('INTERESTED', 'APPLYING', 'APPLIED', 'REJECTED')),
    title             TEXT                     NOT NULL,
    company           TEXT                     NOT NULL,
    url               TEXT                     NOT NULL,
    description       TEXT                     NOT NULL,
    job_language      TEXT                     NOT NULL,
    captured_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    posting_last_seen TIMESTAMP WITH TIME ZONE,
    content_hash      TEXT                     NOT NULL,
    notes             TEXT                     NOT NULL DEFAULT '',
    applied_on        DATE,
    stage_changed_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (owner_sub, posting_id)
);
CREATE INDEX application_owner_changed ON application (owner_sub, stage_changed_at DESC);
--rollback DROP TABLE application;

-- A version of a motivation letter for one application. Versions are never overwritten; deleting the application deletes them.
--changeset moldo:017-motivation-letter
CREATE TABLE motivation_letter
(
    id               UUID                     NOT NULL PRIMARY KEY,
    application_id   UUID                     NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    owner_sub        TEXT                     NOT NULL,
    version          INT                      NOT NULL CHECK (version > 0),
    language         TEXT                     NOT NULL,
    text             TEXT                     NOT NULL,
    how              TEXT                     NOT NULL CHECK (how IN ('GENERATED', 'REGENERATED', 'EDITED')),
    selection        JSONB,
    instructions     TEXT,
    comments         JSONB,
    based_on_version INT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (application_id, version)
);
CREATE INDEX motivation_letter_owner ON motivation_letter (owner_sub, application_id);
--rollback DROP TABLE motivation_letter;
