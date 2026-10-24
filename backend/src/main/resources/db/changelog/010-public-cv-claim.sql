--liquibase formatted sql

-- Every handle claim (first claim and claim after a removal) is audited, so the 30-day handle rule survives deleting the page.
--changeset moldo:015-public-cv-audit-handle-claimed
ALTER TABLE public_cv_audit DROP CONSTRAINT public_cv_audit_event_check;
ALTER TABLE public_cv_audit ADD CONSTRAINT public_cv_audit_event_check
    CHECK (event IN ('PUBLISHED', 'PAUSED', 'SETTINGS_CHANGED', 'HANDLE_CHANGED', 'HANDLE_CLAIMED', 'REMOVED', 'TAKEN_DOWN', 'UNLOCKED'));
--rollback ALTER TABLE public_cv_audit DROP CONSTRAINT public_cv_audit_event_check;
--rollback ALTER TABLE public_cv_audit ADD CONSTRAINT public_cv_audit_event_check CHECK (event IN ('PUBLISHED', 'PAUSED', 'SETTINGS_CHANGED', 'HANDLE_CHANGED', 'REMOVED', 'TAKEN_DOWN', 'UNLOCKED'));
