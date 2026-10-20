--liquibase formatted sql

-- Only the SHA-256 hash of a reset token is stored, never the token itself.
--changeset moldo:003-password-reset-token
CREATE TABLE password_reset_token
(
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    token_hash TEXT                     NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    version    BIGINT                   NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT ux_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_password_reset_token_user ON password_reset_token (user_id);
--rollback DROP TABLE password_reset_token;
