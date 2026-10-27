# applications

Purpose: the user's tracked jobs. Marking a posting interested stores a frozen copy of it (title, company, link, plain-text description, capture time) with a stage and notes, so the text stays even when the shared posting changes or disappears. Motivation letters (the `motivation_letter` table) are versions of text per application, written from the frozen description and only the chosen parts of the saved profile.

## Public API (this package)
- `ApplicationService`: `track(sub, postingId)` (idempotent, `null` for an unknown posting, `ApplicationLimitException` for the 501st), `get`, `list(sub, stage?)`, `update(sub, id, ApplicationPatch)` (`InvalidApplicationPatchException` with a fixed code), `remove`, `byPosting(sub, ids)` (up to 100), `frozenJob(sub, id)`, `ravProof(sub, id)` (`RavProof`, the Swiss RAV job-search proof fields; `ApplicationNotAppliedException` unless stage APPLIED).
- Types: `ApplicationStage` (INTERESTED, APPLYING, APPLIED, REJECTED), `LiveStatus` (OPEN, CHANGED, GONE), `ApplicationSummary`, `ApplicationDetail`, `ApplicationPatch`, `TrackResult`, `PostingTracking`, `FrozenJob`.
- Live status: `GONE` when the posting no longer exists or was not seen for 14 days; `CHANGED` when its content hash differs from the one captured; else `OPEN`.

- `LetterService`: `generate(sub, appId, GenerateLetterCommand)` (first draft, or regeneration when `basedOnVersion` is set), `saveEdited`, `list` (oldest first), `get`, `file`, `delete`; null = not the caller's. Exceptions `LetterSelectionInvalidException`, `LetterTextInvalidException`, `LetterLimitException`, `LetterRateLimitedException`; `LlmFailure` passes through. `paragraphs(text)` is the paragraph rule (same table as the UI).

## Internal (`internal/`)
- `ApplicationController`: `POST /api/applications` (201 new, 200 existing), `GET /api/applications?stage=`, `GET /api/applications/by-posting?ids=`, `GET`, `PATCH`, `DELETE /api/applications/{id}`, `GET /api/applications/{id}/rav` (409 `not_applied` unless APPLIED; the company address, contact person, phone, e-mail and postal code are not stored, so they are null; position and posting link come from the frozen copy). The owner is always the token's `sub`; another user's id is 404; errors are `{"error": code}` (`stage_invalid`, `notes_invalid`, `applied_on_invalid`, `ids_invalid`, `application_limit`).
- `DefaultApplicationService`: the rules (stage change time, applied date defaults to today when entering APPLIED, notes at most 5 000 characters, 500 applications per user serialised by an advisory lock per user).
- `LetterController`: `POST /api/applications/{id}/letters` (201 `LetterDetail`), `POST .../letters/edited` `{text}`, `GET .../letters` (bare array), `GET .../letters/{version}`, `GET .../letters/{version}/download` (`text/markdown; charset=utf-8`, attachment, nosniff, no-store), `DELETE .../letters/{version}` (204). Errors `{"error": code}`: `selection_invalid` 400, `letter_text_invalid` 400, `letter_limit` 409, `rate_limited` 429, `letter_failed` 502, `letter_not_configured` 503, 404 for others' or unknown ids.
- `DefaultLetterService`: indexes resolve against the saved profile (`ProfileService.get(sub)`, never the client); the candidate is the full name plus the chosen entries only; language defaults to the posting's; caps 8 experience, 6 projects, 40 skills, instructions 1 000, 20 comments of 500; the model call runs outside a transaction and the version number and the 20 cap are settled under an advisory lock. The cap is checked before the limiter, so a refused request costs no call.
- `LetterLimiter` (`jobhunter.letters.per-hour`, 20): generate and regenerate only. `LetterFileName`: `motivation-<company>-<title>-v<version>.md`, same normalisation as the CV export (copied, tested).
- `SkillAnalysisService` / `DefaultSkillAnalysisService` / `SkillMatcher`: skill analysis of an application (change `letter-skill-gaps`). The model (via `llm.SkillExtractor`) only lists the skills the frozen description asks for; `SkillMatcher` decides which the caller has (normalised names plus a fixed alias table) and the split `include` / `gaps` / `other` is derived from the current saved profile on every read. Gap notes (max 20, 500 chars each, per application, never written to the profile) are read by `DefaultLetterService` itself and passed to the letter writer as `gapNotes`.
- Evidence (change `skill-gap-evidence`): the analysis call also gets numbered positions (title, employer, achievements, technologies) and projects (name, description, summary) built by `EvidenceInputBuilder` (caps: 300 chars per achievement, 12 positions, 10 projects, 15 000 in all; never name, contact, salary, preferences or work authorisation). Each asked skill gets a status derived on every read from the current profile: `LISTED` (name or alias matches a saved skill, code only), `EVIDENCED` (the model's cited entry exists, its quote occurs in the entry and the entry names the skill or an alias as a whole word: `EvidenceCheck`), else `GAP`. Old stored rows have no evidence and read without `evidenced`. A letter may carry `evidencedSkills` (names); `DefaultLetterService` re-resolves them against the stored analysis and profile (400 `selection_invalid` otherwise) and passes source and quote from the profile, never from the request.
- `SkillsController`: `GET /api/applications/{id}/skills` (`{analysed:false}` or the analysis), `POST .../skills/analyse` (model call, replaces, keeps notes of skills that are still gaps), `PUT .../skills/notes` `{notes:[{skill,text}]}`. Errors: `notes_invalid` 400, `rate_limited` 429, `skills_failed` 502, `skills_not_configured` 503, 404 for others' or unknown ids.
- `SkillsLimiter` (`jobhunter.letters.skills-per-hour`, 20): analyses only, separate from `LetterLimiter`; reading and saving notes do not count.
- `LetterRepository`, `ApplicationRepository`: JDBC, every statement scoped by owner.

## Events
- None.

## Tables
- `application`, `motivation_letter` (`db/changelog/011-applications.sql`, changesets `moldo:016` and `moldo:017`), `letter_skill_analysis` (`013-letter-skill-gaps.sql`, changeset `moldo:019`).

## Calls
- `accounts` (`CurrentUser`, `UserHourlyLimiter`), `postings` (`PostingQuery.find`, `liveState`), `profile` (`ProfileService.get`), `llm` (`LetterWriter`). Nothing may depend on this module.

## Logs
- Outcomes only ("Application tracked", "updated", "removed", "Letter created: version, how, ms"); never a title, description, notes, profile text, comment or letter.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `ApplicationsApiTests` (every scenario of the `applications` spec, including the log check), `LettersApiTests` (every scenario of the `motivation-letter` spec with a recording fake `LetterWriter`), `ParagraphsTests` (reads `ui/src/applications/paragraphs.cases.json`), `LetterFileNameTests`, `DatabaseBaselineTests`, `ApiDocsContentTests`, `ModularityTests`.

`trackImported(sub, JobData)` tracks a job that has no posting (a user import): `source = IMPORT`, `posting_id` empty, live status always OPEN, unique per user and canonical link (a repeat returns the existing application with `created=false`). Letters and every owner-scoped query work on it like on any other application.
