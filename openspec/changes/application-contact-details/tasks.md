# Tasks

## 1. Schema and application model

- [x] 1.1 Add Liquibase changeset `014-application-contact.sql` (six nullable columns on `application` and `job_import_draft`, with `--rollback`) and include it in the master changelog; verify the integration tests start with it and an existing row reads `null` for all six
- [x] 1.2 Add `ContactDetails` to the `applications` public API; carry it in `JobData`, `ApplicationDetail` and `ApplicationPatch`, and persist it in `ApplicationRepository`; verify with a repository/API test that the details are stored on `trackImported` and returned by `GET /api/applications/{id}`, and are absent from the list
- [x] 1.3 Patch rules: set, blank clears to `null`, omitted unchanged, limits and email shape, 400 with a fixed code; verify with `ApplicationsApiTests` cases for set, clear, invalid email and another user's application (404)
- [x] 1.4 `ravProof` reads the stored details and composes `companyAddress` ("street, postal code city", missing parts skipped, all missing is `null`); verify with tests for all stored, only city, only postal code and nothing stored, and update the `applications` README

## 2. Extraction and grounding

- [x] 2.1 Extend `JobDraft` with the six optional fields, update `KoogJobExtractor`'s parsing and `JobPrompts` with the contact rules; verify the prompt tests pin the rules (generic footer excluded, null when not stated, page text is data) and a parse test covers missing keys
- [x] 2.2 Add `JobContactGrounding` in `jobimport/internal` with the rules of the job-import spec; verify with unit tests per field: kept when present, dropped when absent, phone with spaces, postal code shapes, a multi-word street, and a value planted in the text
- [x] 2.3 Store the six fields on the draft, return them in `ImportPreview`, accept them in `ImportEdits` (blank clears, limits as in 1.3, `invalid_edit` otherwise) and pass them to `applications.trackImported` on confirm; verify with `JobImport` API tests for preview, edited confirm, invalid edit and another user's draft (404), and that no log line contains a detail (extend the existing logging test)
- [x] 2.4 Update the `jobimport` and `llm` READMEs; verify `ModularityTests` and `ModuleViolationTests` pass

## 3. UI

- [x] 3.1 Add a shared `CompanyContactFields` component and use it in the import review step; send the edits on confirm and keep typed values on `invalid_edit`; verify with `ImportJob.test.tsx` (found values filled, edit sent, invalid edit keeps input)
- [x] 3.2 Add the "Company & contact" section to `ApplicationPage` saved with the notes, empty fields shown empty, blank sent to clear; verify with `ApplicationPage.test.tsx` (edit, clear, refused save) and `applicationsApi.test.ts`
- [x] 3.3 RAV card: show the stored values, keep "null" and the disabled copy for missing ones, and add the pointer to the section; verify with `RavProofCard.test.tsx`

## 4. Integration

- [x] 4.1 Run `./gradlew test integrationTest`, `tsc -b` and the full `vitest run`; verify all pass
- [ ] 4.2 Deploy backend then UI to s4v3.net, import a real job ad and check the review step, the application page and the RAV card; verify the six values match the ad or are empty

## Workflow follow-up

- Archive the change after review and sync the delta specs into `openspec/specs`.
- Follow-up change: extract the same details for crawled-source postings (SwissDevJobs and others) and offer a re-scan for existing applications.
