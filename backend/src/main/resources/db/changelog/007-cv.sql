--liquibase formatted sql

-- One uploaded CV per user, replaced on upload and kept until the user deletes it. The file is personal data: it is
-- deleted with the user and never logged. `file_name` is a sanitised display name only and is never used as a path.
--changeset moldo:010-cv-document
CREATE TABLE cv_document
(
    owner_sub    TEXT                     NOT NULL PRIMARY KEY REFERENCES app_user (sub) ON DELETE CASCADE,
    file_name    TEXT                     NOT NULL,
    content_type TEXT                     NOT NULL CHECK (content_type IN ('application/pdf', 'text/markdown', 'text/plain')),
    size_bytes   INT                      NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    sha256       TEXT                     NOT NULL,
    content      BYTEA                    NOT NULL,
    uploaded_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
--rollback DROP TABLE cv_document;
