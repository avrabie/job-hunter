# postings

Purpose: the shared store of canonical job postings, with idempotent ingest, cross-source dedupe and language detection. Postings have no owner.

## Public API (this package)
- `PostingIngest.ingest(PostingCandidate)` returns `IngestResult` (`CREATED`, `UPDATED`, `UNCHANGED`). Throws `PostingRejectedException` for a blank required field.
- `PostingQuery`: `find`, `list`, `search(PostingSearch)`, `count(PostingSearch)`, `liveState(ids)` (last seen and content hash only); page size capped at 100. `PostingSearch` filters by text, canton, source, language and first-seen bounds (from inclusive, before exclusive); order is first seen newest first, then id.
- `Posting`, `PostingLink`, `PostingSearch`, `RemoteMode`.
- `JobIngested(postingId, ingestedAt)`: event.

## Internal (`internal/`)
- `JdbcPostings`: implements both interfaces; advisory lock per (source, source id); only the primary source may change the canonical fields.
- `Normalization`: text normalising, dedupe key, content hash, workload parsing.
- `LanguageDetector`: Lingua, en/de/fr/it; unsure becomes `other` plus needs-review; fr, it and other are out of scope.

- `PublicPostingsController`: `GET /api/public/postings`, no token. Own response type with id, title, company, city, canton, remoteMode, salaryMin, salaryMax, language, firstSeen and links (label, url), plus total, page, size: never the description, raw payload or last-seen. Parameters: `page`, `size` (default 20, at most 50), `days` (1 to 31) or `since`/`until` (ISO dates, Zurich time, inclusive, at most 31 days, `since` at most 90 days ago; `days` together with a date is a 400), `canton`, `language` (en or de); default window the last 14 days; `page * size` at most 1000; else 400. Answers `Cache-Control: public, max-age=60`. Permitted for GET in `accounts` `SecurityConfig`.
- `PublicPostingsLimiter`: in-memory sliding window per client (first `X-Forwarded-For` value, else the peer address), `jobhunter.postings.public.per-minute` (default 60), 429 with `Retry-After`. At most 10000 addresses are kept; the least recently used is dropped when full. Addresses are never logged.

## Events
- Publishes `JobIngested` only for a new canonical posting (not for updates or a posting found on a second source). Consumes none.

## Tables
- `posting`, `posting_source` (`db/changelog/003-postings.sql`); the existing `posting_first_seen_idx` serves the public window, no new index.

## Calls
- No other module.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `PostingIngestTests`, `PostingSearchTests`, `PostingSchemaTests`, `PostingsApiTests`, `PostingLoggingTests`, `PostingsLoggingTests`, `PublicPostingsApiTests`, `PublicPostingsLoggingTests`.
- Unit: `NormalizationTests`, `LanguageDetectorTests`, `PublicPostingsLimiterTests`.

Read on 2026-10-27 from: postings/*.kt, postings/internal/PublicPostings*.kt, postings/internal/JdbcPostings.kt, Normalization.kt, LanguageDetector.kt, db/changelog/003-postings.sql, test file names.
