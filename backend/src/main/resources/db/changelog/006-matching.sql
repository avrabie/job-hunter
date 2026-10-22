--liquibase formatted sql

-- The prefilter's result per user and posting. Derived from the user's profile, so it is personal data: it is deleted
-- with the user (and with the posting) and holds only numbers and fixed reason codes, never profile or posting text.
--changeset moldo:009-posting-match
CREATE TABLE posting_match
(
    user_sub        TEXT                     NOT NULL REFERENCES app_user (sub) ON DELETE CASCADE,
    posting_id      UUID                     NOT NULL REFERENCES posting (id) ON DELETE CASCADE,
    passed          BOOLEAN                  NOT NULL,
    score           INT                      NOT NULL CHECK (score BETWEEN 0 AND 100),
    reasons         TEXT[]                   NOT NULL,
    profile_version BIGINT                   NOT NULL,
    evaluated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_sub, posting_id)
);
CREATE INDEX posting_match_user_passed_score_idx ON posting_match (user_sub, passed, score DESC);
--rollback DROP TABLE posting_match;
