--liquibase formatted sql

-- A job ad a user pasted or fetched from a link, held until they confirm it. Drafts are short-lived (`expires_at`) and
-- belong to one user (`owner`, the token subject); a confirm reads a draft by id and owner only.
-- A confirmed import becomes an application of that user only, never a shared posting: `application.source` is
-- `IMPORT` and `posting_id` stays empty (the column is nullable already). Per user, a link is imported once.
--changeset moldo:018-job-import
CREATE TABLE job_import_draft
(
    id          UUID                     NOT NULL PRIMARY KEY,
    owner       TEXT                     NOT NULL,
    url         TEXT                     NOT NULL,
    title       TEXT                     NOT NULL,
    company     TEXT                     NOT NULL,
    location    TEXT,
    description TEXT                     NOT NULL,
    language    TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX job_import_draft_owner ON job_import_draft (owner);

ALTER TABLE application
    ADD COLUMN source TEXT NOT NULL DEFAULT 'POSTING' CHECK (source IN ('POSTING', 'IMPORT'));
CREATE UNIQUE INDEX application_owner_import_url ON application (owner_sub, url) WHERE source = 'IMPORT';
--rollback DROP INDEX application_owner_import_url;
--rollback ALTER TABLE application DROP COLUMN source;
--rollback DROP TABLE job_import_draft;
