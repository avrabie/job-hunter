--liquibase formatted sql

-- A user's public CV page: at most one row per owner, and no row means no page. `settings` holds the owner's choice of
-- what is public (flags and section names, never the profile). `handle` is stored lower-case and unique ignoring case.
-- `locked` is set only by an administrator's takedown and is kept by the owner's own saves. Personal data: deleted with the user.
--changeset moldo:012-public-cv
CREATE TABLE public_cv
(
    owner_sub         TEXT                     NOT NULL PRIMARY KEY REFERENCES app_user (sub) ON DELETE CASCADE,
    handle            TEXT                     NOT NULL CHECK (handle = lower(handle)),
    settings          JSONB                    NOT NULL,
    published         BOOLEAN                  NOT NULL,
    locked            BOOLEAN                  NOT NULL DEFAULT FALSE,
    settings_version  INT                      NOT NULL,
    handle_changed_at TIMESTAMP WITH TIME ZONE,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX public_cv_handle_lower_idx ON public_cv (lower(handle));
--rollback DROP TABLE public_cv;

-- A released handle, kept for its previous owner (who may take it back) until `held_until`; nobody else can claim it
-- meanwhile. Expired rows are purged by a daily job. Personal data: deleted with the owner.
--changeset moldo:013-public-cv-handle-hold
CREATE TABLE public_cv_handle_hold
(
    handle     TEXT                     NOT NULL PRIMARY KEY CHECK (handle = lower(handle)),
    owner_sub  TEXT                     NOT NULL REFERENCES app_user (sub) ON DELETE CASCADE,
    held_until TIMESTAMP WITH TIME ZONE NOT NULL
);
--rollback DROP TABLE public_cv_handle_hold;

-- What the owner did to the page. `flags` records which settings are on, never their values. Personal data: deleted
-- with the owner.
--changeset moldo:014-public-cv-audit
CREATE TABLE public_cv_audit
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_sub   TEXT                     NOT NULL REFERENCES app_user (sub) ON DELETE CASCADE,
    event       TEXT                     NOT NULL CHECK (event IN ('PUBLISHED', 'PAUSED', 'SETTINGS_CHANGED', 'HANDLE_CHANGED', 'REMOVED', 'TAKEN_DOWN', 'UNLOCKED')),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    flags       JSONB                    NOT NULL
);
CREATE INDEX public_cv_audit_owner_idx ON public_cv_audit (owner_sub, occurred_at DESC);
--rollback DROP TABLE public_cv_audit;
