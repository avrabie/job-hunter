# matching

Purpose: the deterministic prefilter. For every (user, posting) it stores whether the posting passes the user's hard rules and a 0 to 100 score. No language model is used.

## Public API (this package)
- None (no classes in the base package). It is driven by events and has one HTTP endpoint.

## Internal (`internal/`)
- `MatchListeners`: `@ApplicationModuleListener` for `JobIngested` and `ProfileSaved`.
- `MatchEvaluator`: `evaluatePosting` (all users, paged via `ProfileDirectory`), `evaluateProfile` (all postings for one user; stops when a newer profile version appears).
- `Prefilter`: pure rules (out of scope, canton, workload, remote mode, minimum salary) plus score (role 50, skills 40, language 10).
- `MatchStore`: `posting_match` upsert and reads, always scoped by `user_sub`.
- `MatchesController`: `GET /api/matches` (passing matches, best score first).
- `MatchingSettings`.

## Events
- Consumes `JobIngested` (postings) and `ProfileSaved` (profile). Publishes none.

## Tables
- `posting_match` (`db/changelog/006-matching.sql`). Holds numbers and fixed reason codes only.

## Settings
- `jobhunter.matching.threshold` (default 30).

## Calls
- `accounts`, `postings` (`PostingQuery`), `profile` (`ProfileService`, `ProfileDirectory`).

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `MatchingFlowTests`, `MatchesApiTests`, `PostingMatchSchemaTests`, `MatchingLoggingTests`, `MatchingTestSupport`.
- Unit: `PrefilterTests`.

Read on 2026-10-24 from: matching/internal/*.kt, db/changelog/006-matching.sql, test file names.
