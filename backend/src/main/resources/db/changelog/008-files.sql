--liquibase formatted sql

-- The user's file cabinet: any number of files per user, each up to 10 GiB, kept on the files volume. The row holds the
-- metadata only; the bytes live at `storage_key`, which is generated and never built from `name`. `name` is a display
-- name and is never used as a path. At most one file per user is the master, and only a CV can be one.
--changeset moldo:011-user-file
CREATE TABLE user_file
(
    id           UUID                     NOT NULL PRIMARY KEY,
    owner_sub    TEXT                     NOT NULL REFERENCES app_user (sub) ON DELETE CASCADE,
    name         TEXT                     NOT NULL,
    kind         TEXT                     NOT NULL CHECK (kind IN ('CV', 'OTHER')),
    content_type TEXT                     NOT NULL,
    size_bytes   BIGINT                   NOT NULL CHECK (size_bytes BETWEEN 0 AND 10737418240),
    sha256       TEXT                     NOT NULL,
    storage_key  TEXT                     NOT NULL UNIQUE,
    master       BOOLEAN                  NOT NULL DEFAULT FALSE,
    uploaded_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (NOT master OR kind = 'CV')
);
CREATE UNIQUE INDEX user_file_one_master ON user_file (owner_sub) WHERE master;
CREATE INDEX user_file_owner ON user_file (owner_sub, uploaded_at DESC);
--rollback DROP TABLE user_file;
