--liquibase formatted sql

-- The crawl schedule an administrator set through the API; at most one row. Without a row the settings apply.
-- `updated_by` is the administrator's token `sub`, never an email or name.
--changeset moldo:008-crawl-schedule
CREATE TABLE crawl_schedule
(
    id         INT                      NOT NULL PRIMARY KEY CHECK (id = 1),
    enabled    BOOLEAN                  NOT NULL,
    cron       TEXT                     NOT NULL,
    zone       TEXT                     NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by TEXT                     NOT NULL
);
--rollback DROP TABLE crawl_schedule;
