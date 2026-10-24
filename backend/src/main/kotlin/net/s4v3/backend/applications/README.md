# applications

Purpose: the user's tracked jobs. Marking a posting interested stores a frozen copy of it (title, company, link, plain-text description, capture time) with a stage and notes, so the text stays even when the shared posting changes or disappears. Motivation letters (the `motivation_letter` table) are a later task.

## Public API (this package)
- `ApplicationService`: `track(sub, postingId)` (idempotent, `null` for an unknown posting, `ApplicationLimitException` for the 501st), `get`, `list(sub, stage?)`, `update(sub, id, ApplicationPatch)` (`InvalidApplicationPatchException` with a fixed code), `remove`, `byPosting(sub, ids)` (up to 100), `frozenJob(sub, id)`.
- Types: `ApplicationStage` (INTERESTED, APPLYING, APPLIED, REJECTED), `LiveStatus` (OPEN, CHANGED, GONE), `ApplicationSummary`, `ApplicationDetail`, `ApplicationPatch`, `TrackResult`, `PostingTracking`, `FrozenJob`.
- Live status: `GONE` when the posting no longer exists or was not seen for 14 days; `CHANGED` when its content hash differs from the one captured; else `OPEN`.

## Internal (`internal/`)
- `ApplicationController`: `POST /api/applications` (201 new, 200 existing), `GET /api/applications?stage=`, `GET /api/applications/by-posting?ids=`, `GET`, `PATCH`, `DELETE /api/applications/{id}`. The owner is always the token's `sub`; another user's id is 404; errors are `{"error": code}` (`stage_invalid`, `notes_invalid`, `applied_on_invalid`, `ids_invalid`, `application_limit`).
- `DefaultApplicationService`: the rules (stage change time, applied date defaults to today when entering APPLIED, notes at most 5 000 characters, 500 applications per user serialised by an advisory lock per user).
- `ApplicationRepository`: JDBC, every statement scoped by owner.

## Events
- None.

## Tables
- `application`, `motivation_letter` (`db/changelog/011-applications.sql`, changesets `moldo:016` and `moldo:017`).

## Calls
- `accounts` (`CurrentUser`), `postings` (`PostingQuery.find`, `liveState`). Nothing may depend on this module.

## Logs
- Outcomes only ("Application tracked", "updated", "removed"); never a title, description or notes.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `ApplicationsApiTests` (every scenario of the `applications` spec, including the log check), `DatabaseBaselineTests`, `ApiDocsContentTests`, `ModularityTests`.
