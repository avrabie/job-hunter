# public-cv-page, group 1 (tasks 1.1 to 1.3), backend

## Tasks
- 1.1 Done. Migration plus `PublicCvSchemaTests`: 5 integration tests pass (`./gradlew integrationTest --tests "PublicCvSchemaTests"`).
- 1.2 Done. `HandleRulesTests` 5 pass, `ReservedHandlesTests` 1 passes (`./gradlew test --tests ...publiccv.internal.*`).
- 1.3 Done. `PublicCvRepositoryTests`: 12 integration tests pass.
- Ticked 1.1, 1.2, 1.3 in `openspec/changes/public-cv-page/tasks.md`. The file changed on disk during my run; I ticked the current state and did not revert anything.
- Not run: the whole suite, `ModularityTests`, `ApiDocsContentTests` (they are not my classes).

## Files
- `backend/src/main/resources/db/changelog/009-public-cv.sql` (new): `public_cv`, `public_cv_handle_hold`, `public_cv_audit`.
- `backend/src/main/resources/db/changelog/db.changelog-master.yaml` (include added after 008).
- `backend/src/main/kotlin/net/s4v3/backend/cv/publiccv/PublicCvTypes.kt` (new): `PublicCvSettings`, `PublicCvContact`, `PublicCvState`.
- `backend/src/main/kotlin/net/s4v3/backend/cv/publiccv/internal/HandleRules.kt` (new): `HandleRules` (normalize, validate, `RESERVED`), `HandleProblem { INVALID, RESERVED }`.
- `backend/src/main/kotlin/net/s4v3/backend/cv/publiccv/internal/PublicCvRepository.kt` (new): `PublicCvRepository`, `PublicCvRow`, `HandleHold`, `PublicCvEvent`.
- Tests: `backend/src/test/kotlin/net/s4v3/backend/PublicCvSchemaTests.kt`, `PublicCvRepositoryTests.kt`, and `backend/src/test/kotlin/net/s4v3/backend/cv/publiccv/internal/HandleRulesTests.kt`, `ReservedHandlesTests.kt`.
- Not committed.

## Changeset ids
- Existing ids are one global counter, not one per file: 001 to 003 in the baseline, 004 profile, 005 posting, 006 posting_source, 007 source_fetch_state, 008 crawl_schedule, 009 posting_match, 010 cv_document, 011 user_file.
- New: `moldo:012-public-cv`, `moldo:013-public-cv-handle-hold`, `moldo:014-public-cv-audit`, all with `--rollback DROP TABLE ...`. The file number (009) and the changeset numbers (012 to 014) differ, as they already do for 008-files (011).

## Decisions and surprises for the lead
- The change rule reads the audit table (`changeTooSoon(sub, now)`: a `HANDLE_CHANGED` audit row in the last 30 days), not `handle_changed_at`. Reason: delete then claim again would otherwise reset the rule. The service must write `HANDLE_CHANGED` on every handle change. Whether a first claim counts is the service's decision; I did not decide it.
- `DELETE` removes the `public_cv` row, so an admin lock disappears with it. The service must refuse delete while `locked`, or the owner can delete and republish.
- `upsert` never writes `locked`; an owner save cannot undo a takedown. But it can set `published = true` on a locked row. The service must answer 409 `locked` before publishing.
- `takeDown` and `unlock` answer the owner's `sub` (or null), so the service can audit against the owner.
- Settings reuse `CvTemplate`, `CvLanguage` and `CvSectionKind` from `cv.export` (no new enums). Contact is a nested `PublicCvContact`.
- `HandleRules.validate(raw)` lower-cases the input itself. Digits-only handles answer `INVALID` (400), as the spec's "123456" scenario requires; `RESERVED` (409) is only for the list.
- Stored settings are read with Jackson 3 leniently (unknown properties ignored); the strict parse belongs to the controller.
- `purgeHolds` test asserts `>= 1` because the Postgres container is shared by the JVM's integration tests.
