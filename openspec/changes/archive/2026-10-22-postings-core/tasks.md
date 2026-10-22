# Tasks

## 1. Schema

- [x] 1.1 Add `backend/src/main/resources/db/changelog/003-postings.sql` with changesets (ids from `005`) for `posting` (normalized fields, `raw_payload TEXT`, `language`, `out_of_scope`, `dedupe_key`, `content_hash`, first/last seen) and `posting_source` (`UNIQUE (source_id, source_posting_id)`, FK to `posting`, `is_primary`, URL, last seen), each with `--rollback`; include it in `db.changelog-master.yaml`. Verify with a schema test (like `ProfileSchemaTests`) that checks tables, the unique constraint and the FK, and that the migration applies on `pgvector/pgvector:pg16`.

## 2. Normalization and dedupe key

- [x] 2.1 Implement normalization (diacritics, case, punctuation, gender markers, whitespace) and the dedupe key in `postings/internal`. Verify with unit tests: "Senior Kotlin Developer (m/w/d)" and "senior kotlin developer" at "Acme AG"/"ACME AG" in "Zürich"/"Zurich" give one key; a different city gives a different key; a missing city gives no key.
- [x] 2.2 Implement the content hash and workload parsing ("80-100%", "100%", absent). Verify with unit tests for each form and for hash equality/inequality on description changes.

## 3. Language detection

- [x] 3.1 Add Lingua to `backend/build.gradle.kts` with a version pinned from Maven Central, and implement a detector over EN/DE/FR/IT with the short-text and low-confidence fallback to `other`. Verify with unit tests using short fixture texts (DE, EN, FR, a 20-character text, and a mixed DE/EN IT posting).
- [x] 3.2 Measure heap with the detector loaded and check it against the backend pod memory limit in `k8s/`. Verify by recording the number in the design's risk note and, if the limit is too low, raising it in the overlay.

## 4. Ingest and query

- [x] 4.1 Define the public API in the `postings` package: `PostingCandidate`, `IngestResult`, `PostingIngest`, `Posting`, `PostingQuery`, and the `JobIngested` event. Verify that `ModularityTests` still passes and that no public type references `postings.internal`.
- [x] 4.2 Implement `PostingIngest` in `postings/internal` with the advisory lock, required-field rejection, create/update/unchanged, cross-source linking, raw payload replacement and language re-detection on update. Verify with integration tests for each spec scenario: first sight, repeat, changed description, minimal posting, missing field, cross-source link, different city, missing city, canonical content not overwritten.
- [x] 4.3 Publish `JobIngested` only when a new canonical row is created, inside the ingest transaction. Verify with tests that the event publication registry holds exactly one event after a create and none after a repeat or cross-source link.
- [x] 4.4 Verify concurrent ingest of one new (source id, source posting id) from two threads leaves one row and no failure (a test in the style of `ConcurrentProvisioningTests`).
- [x] 4.5 Implement `PostingQuery` (lookup by id without raw payload, newest-first paged listing with a size cap). Verify with tests for lookup, unknown id, a page of 10 from 30 postings, and identical results when called on behalf of two different users.

## 5. Privacy and docs

- [x] 5.1 Add a log-capture test, in the style of `ProfileLoggingTests`, asserting that no description or raw payload text appears in logs during create, update and rejected ingests.
- [x] 5.2 Update `PLAN.md` module table (`postings`: built parts) and `backend/README.md` with the ingest API for adapter authors. Verify the README example compiles or is copied from a test.

## 6. Integration

- [x] 6.1 Run the full backend build and test suite (`./gradlew build` in `backend/`) and verify it passes, including `ModularityTests`.

## Workflow follow-up

- Deploy the backend image to s4v3.net and check the migration ran (health endpoint up, tables present), per the project's deploy-and-test-on-prod practice.
- Archive the change after review, then propose `source-<ats>` as the first adapter.
