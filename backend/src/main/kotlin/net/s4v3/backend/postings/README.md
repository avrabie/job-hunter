# postings

Purpose: the shared store of canonical job postings, with idempotent ingest, cross-source dedupe and language detection. Postings have no owner.

## Public API (this package)
- `PostingIngest.ingest(PostingCandidate)` returns `IngestResult` (`CREATED`, `UPDATED`, `UNCHANGED`). Throws `PostingRejectedException` for a blank required field.
- `PostingQuery`: `find`, `list`, `search(PostingSearch)`; page size capped at 100.
- `Posting`, `PostingLink`, `PostingSearch`, `RemoteMode`.
- `JobIngested(postingId, ingestedAt)`: event.

## Internal (`internal/`)
- `JdbcPostings`: implements both interfaces; advisory lock per (source, source id); only the primary source may change the canonical fields.
- `Normalization`: text normalising, dedupe key, content hash, workload parsing.
- `LanguageDetector`: Lingua, en/de/fr/it; unsure becomes `other` plus needs-review; fr, it and other are out of scope.

## Events
- Publishes `JobIngested` only for a new canonical posting (not for updates or a posting found on a second source). Consumes none.

## Tables
- `posting`, `posting_source` (`db/changelog/003-postings.sql`).

## Calls
- No other module.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `PostingIngestTests`, `PostingSearchTests`, `PostingSchemaTests`, `PostingsApiTests`, `PostingLoggingTests`, `PostingsLoggingTests`.
- Unit: `NormalizationTests`, `LanguageDetectorTests`.

Read on 2026-10-24 from: postings/*.kt, postings/internal/JdbcPostings.kt, Normalization.kt, LanguageDetector.kt, db/changelog/003-postings.sql, test file names.
