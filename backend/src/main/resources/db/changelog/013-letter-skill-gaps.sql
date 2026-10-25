--liquibase formatted sql

-- The skill analysis of an application (what the job description asks for, as the model said it) and the candidate's notes on
-- the gaps. One row per application, owned by the same user (`owner_sub`, the token subject); deleting the application deletes
-- the row. The split into include / gaps / other is not stored: it is derived from the current profile on every read.
--changeset moldo:019-letter-skill-gaps
CREATE TABLE letter_skill_analysis
(
    application_id UUID                     NOT NULL PRIMARY KEY REFERENCES application (id) ON DELETE CASCADE,
    owner_sub      TEXT                     NOT NULL,
    analysis       JSONB                    NOT NULL,
    notes          JSONB                    NOT NULL DEFAULT '[]'::jsonb,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX letter_skill_analysis_owner ON letter_skill_analysis (owner_sub);
--rollback DROP INDEX letter_skill_analysis_owner;
--rollback DROP TABLE letter_skill_analysis;
