--liquibase formatted sql

-- A job ad a user pasted or fetched from a link, held until they confirm it. Drafts are short-lived (`expires_at`) and
-- belong to one user (`owner`, the token subject); a confirm reads a draft by id and owner only.
-- `posting_import` records which user added a posting by import, for abuse handling only: never returned by any API,
-- removed with the user's account, while the posting itself stays.
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

CREATE TABLE posting_import
(
    posting_id UUID                     NOT NULL PRIMARY KEY REFERENCES posting (id) ON DELETE CASCADE,
    user_sub   TEXT                     NOT NULL,
    url        TEXT                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
--rollback DROP TABLE posting_import;
--rollback DROP TABLE job_import_draft;
