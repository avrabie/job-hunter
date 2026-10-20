--liquibase formatted sql

-- A NULL password means the account has no usable local password (created through Google, or linked to Google).
--changeset moldo:001-users
CREATE TABLE users
(
    id          UUID                     NOT NULL,
    email       TEXT                     NOT NULL,
    password    TEXT,
    first_name  TEXT,
    last_name   TEXT,
    enabled     BOOLEAN                  NOT NULL DEFAULT TRUE,
    provider    TEXT                     NOT NULL DEFAULT 'LOCAL',
    provider_id TEXT,
    version     BIGINT                   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_provider CHECK (provider IN ('LOCAL', 'GOOGLE'))
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX ux_users_provider_subject ON users (provider, provider_id) WHERE provider_id IS NOT NULL;
--rollback DROP TABLE users;

--changeset moldo:002-user-role
CREATE TABLE user_role
(
    user_id UUID NOT NULL,
    role    TEXT NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
--rollback DROP TABLE user_role;
