--liquibase formatted sql

-- The extension is normally created by the cluster's database init script (superuser);
-- IF NOT EXISTS keeps this a no-op there and makes local and test databases work.
--changeset moldo:001-vector-extension
CREATE EXTENSION IF NOT EXISTS vector;
--rollback DROP EXTENSION IF EXISTS vector;

-- Spring Modulith JDBC event publication registry (schema v2 of spring-modulith-events-jdbc 2.1.1).
-- Liquibase owns the schema, so spring.modulith.events.jdbc.schema-initialization stays off.
--changeset moldo:002-event-publication
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
--rollback DROP TABLE event_publication;

-- One row per user, keyed by the JWT `sub` (an opaque id issued by the auth service).
--changeset moldo:003-app-user
CREATE TABLE app_user
(
    sub          TEXT                     NOT NULL PRIMARY KEY,
    email        TEXT,
    display_name TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
--rollback DROP TABLE app_user;
