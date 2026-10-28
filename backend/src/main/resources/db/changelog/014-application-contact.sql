--liquibase formatted sql

-- The hiring company's address and the application contact, read from the job ad (or typed by the user), for the RAV
-- job-search proof. Six optional columns on the application and on the import draft that feeds it. They are personal data
-- of a third party (the contact person): kept on the importing user's rows only, removed with them, never logged.
-- Existing rows read NULL for all six.
--changeset moldo:020-application-contact
ALTER TABLE application
    ADD COLUMN company_street      TEXT,
    ADD COLUMN company_postal_code TEXT,
    ADD COLUMN company_city        TEXT,
    ADD COLUMN contact_person      TEXT,
    ADD COLUMN contact_phone       TEXT,
    ADD COLUMN contact_email       TEXT;
ALTER TABLE job_import_draft
    ADD COLUMN company_street      TEXT,
    ADD COLUMN company_postal_code TEXT,
    ADD COLUMN company_city        TEXT,
    ADD COLUMN contact_person      TEXT,
    ADD COLUMN contact_phone       TEXT,
    ADD COLUMN contact_email       TEXT;
--rollback ALTER TABLE job_import_draft DROP COLUMN company_street, DROP COLUMN company_postal_code, DROP COLUMN company_city, DROP COLUMN contact_person, DROP COLUMN contact_phone, DROP COLUMN contact_email;
--rollback ALTER TABLE application DROP COLUMN company_street, DROP COLUMN company_postal_code, DROP COLUMN company_city, DROP COLUMN contact_person, DROP COLUMN contact_phone, DROP COLUMN contact_email;
