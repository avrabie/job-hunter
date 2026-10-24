# applications

Purpose: the user's tracked jobs. Marking a posting interested stores a frozen copy of it (title, company, link, plain-text description, capture time) with a stage and notes, so the text stays even when the shared posting changes or disappears. Motivation letters (the `motivation_letter` table) are versions of text per application, written from the frozen description and only the chosen parts of the saved profile.

## Public API (this package)
- `ApplicationService`: `track(sub, postingId)` (idempotent, `null` for an unknown posting, `ApplicationLimitException` for the 501st), `get`, `list(sub, stage?)`, `update(sub, id, ApplicationPatch)` (`InvalidApplicationPatchException` with a fixed code), `remove`, `byPosting(sub, ids)` (up to 100), `frozenJob(sub, id)`.
- Types: `ApplicationStage` (INTERESTED, APPLYING, APPLIED, REJECTED), `LiveStatus` (OPEN, CHANGED, GONE), `ApplicationSummary`, `ApplicationDetail`, `ApplicationPatch`, `TrackResult`, `PostingTracking`, `FrozenJob`.
- Live status: `GONE` when the posting no longer exists or was not seen for 14 days; `CHANGED` when its content hash differs from the one captured; else `OPEN`.

- `LetterService`: `generate(sub, appId, GenerateLetterCommand)` (first draft, or regeneration when `basedOnVersion` is set), `saveEdited`, `list` (oldest first), `get`, `file`, `delete`; null = not the caller's. Exceptions `LetterSelectionInvalidException`, `LetterTextInvalidException`, `LetterLimitException`, `LetterRateLimitedException`; `LlmFailure` passes through. `paragraphs(text)` is the paragraph rule (same table as the UI).

## Internal (`internal/`)
- `ApplicationController`: `POST /api/applications` (201 new, 200 existing), `GET /api/applications?stage=`, `GET /api/applications/by-posting?ids=`, `GET`, `PATCH`, `DELETE /api/applications/{id}`. The owner is always the token's `sub`; another user's id is 404; errors are `{"error": code}` (`stage_invalid`, `notes_invalid`, `applied_on_invalid`, `ids_invalid`, `application_limit`).
- `DefaultApplicationService`: the rules (stage change time, applied date defaults to today when entering APPLIED, notes at most 5 000 characters, 500 applications per user serialised by an advisory lock per user).
- `LetterController`: `POST /api/applications/{id}/letters` (201 `LetterDetail`), `POST .../letters/edited` `{text}`, `GET .../letters` (bare array), `GET .../letters/{version}`, `GET .../letters/{version}/download` (`text/markdown; charset=utf-8`, attachment, nosniff, no-store), `DELETE .../letters/{version}` (204). Errors `{"error": code}`: `selection_invalid` 400, `letter_text_invalid` 400, `letter_limit` 409, `rate_limited` 429, `letter_failed` 502, `letter_not_configured` 503, 404 for others' or unknown ids.
- `DefaultLetterService`: indexes resolve against the saved profile (`ProfileService.get(sub)`, never the client); the candidate is the full name plus the chosen entries only; language defaults to the posting's; caps 8 experience, 6 projects, 40 skills, instructions 1 000, 20 comments of 500; the model call runs outside a transaction and the version number and the 20 cap are settled under an advisory lock. The cap is checked before the limiter, so a refused request costs no call.
- `LetterLimiter` (`jobhunter.letters.per-hour`, 20): generate and regenerate only. `LetterFileName`: `motivation-<company>-<title>.md`, same normalisation as the CV export (copied, tested).
- `LetterRepository`, `ApplicationRepository`: JDBC, every statement scoped by owner.

## Events
- None.

## Tables
- `application`, `motivation_letter` (`db/changelog/011-applications.sql`, changesets `moldo:016` and `moldo:017`).

## Calls
- `accounts` (`CurrentUser`, `UserHourlyLimiter`), `postings` (`PostingQuery.find`, `liveState`), `profile` (`ProfileService.get`), `llm` (`LetterWriter`). Nothing may depend on this module.

## Logs
- Outcomes only ("Application tracked", "updated", "removed", "Letter created: version, how, ms"); never a title, description, notes, profile text, comment or letter.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `ApplicationsApiTests` (every scenario of the `applications` spec, including the log check), `LettersApiTests` (every scenario of the `motivation-letter` spec with a recording fake `LetterWriter`), `ParagraphsTests` (reads `ui/src/applications/paragraphs.cases.json`), `LetterFileNameTests`, `DatabaseBaselineTests`, `ApiDocsContentTests`, `ModularityTests`.
