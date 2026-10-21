--liquibase formatted sql

-- One master profile per user, replaced as a whole document. `version` is the optimistic-lock counter the API
-- compares; `schema_version` lets later changes migrate stored documents. Personal data: deleted with the user.
--changeset moldo:004-profile
CREATE TABLE profile
(
    owner_sub      TEXT                     NOT NULL PRIMARY KEY REFERENCES app_user (sub) ON DELETE CASCADE,
    document       JSONB                    NOT NULL,
    schema_version INT                      NOT NULL,
    version        BIGINT                   NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
--rollback DROP TABLE profile;
