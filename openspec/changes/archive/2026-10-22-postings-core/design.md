# Design

## Context

The `postings` module exists only as `package-info.java` and an `internal` package marker; the `profile` module shows the established layout (Kotlin public API in the module package, implementation in `internal`, JDBC via Spring Data JDBC's `JdbcClient`/`JdbcTemplate`, Liquibase SQL changesets, Testcontainers on `pgvector/pgvector:pg16`). Liquibase changeset ids are numbered globally (`001`-`004` used), so this change adds file `003-postings.sql` with changeset ids from `005`. See proposal.md for motivation and scope.

## Goals / Non-Goals

**Goals:**
- A small public API (`PostingIngest`, `PostingQuery`, `JobIngested`) that source adapters and `matching` can rely on without knowing the schema.
- Ingest that is safe to run concurrently and repeatedly.

**Non-Goals:**
- Choosing how adapters fetch or schedule; the ingest input type is the only contract.
- Fuzzy or semantic dedupe (embeddings come with matching). Dedupe here is a deterministic key.

## Decisions

### Two tables: canonical posting and source reference
`posting` holds the canonical normalized row (including `raw_payload`, `language`, `out_of_scope`, `dedupe_key`, `content_hash`, first/last seen). `posting_source` holds one row per (source id, source posting id) with a `UNIQUE (source_id, source_posting_id)` constraint, `posting_id` FK, URL, and its own last-seen time.

- *Why:* idempotency per source and cross-source linking are different keys; separating them makes "linked, not duplicated" a plain foreign key and keeps the unique constraint the concurrency guard.
- *Alternative:* one table with a unique source key and a nullable `duplicate_of` column. Simpler, but the canonical row would depend on whichever source arrived first and listing would need a filter everywhere.

### Raw payload on the canonical row, from the source that last updated it
Stored as `TEXT` (sources deliver JSON or HTML) so it is returned byte for byte; `JSONB` would reorder keys. A cross-source link does not overwrite the canonical payload; a re-crawl through the source reference that created the canonical row does. To keep that simple, `posting_source` has `is_primary` and only the primary source's updates change canonical fields.

- *Alternative:* a payload per `posting_source`. More faithful to "re-parse later" for every source, but doubles storage for duplicates, and a re-parse of the canonical text only needs the primary payload. Revisit if a second source needs better fields than the first.

### Dedupe key
`dedupe_key = sha256(normCompany | normTitle | normCity)` with normalization: Unicode NFKD, strip diacritics, lowercase, drop gender markers (`(m/w/d)`, `(m/f/d)`, `m/w/x`, `all genders`), drop punctuation, collapse whitespace. Postings without a city get no key and dedupe only within their own source (via the unique source constraint). A unique-index on `dedupe_key` is **not** used, because the same company may legitimately repost; instead, linking looks up the newest canonical row with the key and links to it.

- *Why include city:* the same title at one company in two cities is two jobs; a title-only key would merge them.
- *Risk:* the same ad with different title wording stays duplicated. Accepted for the first version; embeddings can improve it later.

### Change detection by content hash
`content_hash = sha256(title | company | description | location | workload | remote | salary)`. Equal hash means `unchanged` (only last-seen timestamps are touched, no write of the heavy columns). A different hash means `updated` and re-runs language detection.

### Concurrency
Each ingest runs in one transaction that first takes a transaction-scoped advisory lock, `pg_advisory_xact_lock(hashtext(source_id || ':' || source_posting_id))`, then reads, decides and writes. Two ingests of the same source posting are serialized, so the second sees the first's row and returns `unchanged`. The unique constraint on `posting_source` stays as a backstop. The lock needs no retry logic, and the ingest rate is low.

- *Alternative:* `INSERT ... ON CONFLICT DO NOTHING` on `posting_source`. It would also need the canonical row created first and removed if the insert loses the race, which is more code for no gain at this volume.
- *Known gap:* the lock covers one source posting, not the cross-source dedupe key. Two different sources ingesting the same ad at the same moment can both create a canonical row. The next ingest of either does not merge them, so the duplicate stays until an explicit re-link. Accepted for now because sources run one after another in the scheduler; the scheduler change must keep it that way.

### Language detection with Lingua
Add `com.github.pemistahl:lingua` restricted to English, German, French, Italian (the other Swiss-relevant languages are unlikely; anything else scores as unknown and maps to `other`). Detection runs on title plus the first ~2,000 characters of the description. Below a minimum text length (about 40 characters) or when the top confidence is under a threshold, the result is `other` and the posting is flagged for review. Language models are loaded lazily and once; `withLowAccuracyMode` is off because the four-language set is small.

- *Why a library:* the JDK has nothing for this and a hand-written stop-word heuristic fails on mixed DE/EN IT postings, which are common in Switzerland.
- *Alternatives:* Apache Tika's `LanguageDetector` (heavier dependency tree), Optimaize (unmaintained). Lingua is maintained, pure Kotlin/Java and accurate on short text. Version is pinned from Maven Central at apply time.
- *Risk:* memory use (roughly 100 MB with four languages loaded). Acceptable for the one backend replica; checked against the pod limit in the tasks.

### Event
`JobIngested(postingId: UUID, ingestedAt: Instant)` is published with Spring's `ApplicationEventPublisher` inside the ingest transaction so the event publication registry records it atomically with the row. Placed in the module's public package.

### Public API shape
`PostingIngest.ingest(candidate: PostingCandidate): IngestResult` where the candidate carries the normalized fields plus `sourceId`, `sourcePostingId`, `url` and `rawPayload`; `IngestResult` holds `postingId` and outcome (`CREATED`, `UPDATED`, `UNCHANGED`). A posting from a new source that is linked to an existing canonical posting reports `CREATED`, because the source reference is new, but publishes no `JobIngested` event, as the spec requires. `PostingQuery` returns an immutable `Posting` view without the raw payload.

### Module boundaries
No new module and no new cross-module dependency: `postings` depends on nothing but the platform. `ApplicationModules.verify()` stays green; a test asserts nothing outside `postings` imports `postings.internal`. The `accounts` per-user isolation rule does not apply to this shared data, and the spec says so.

## Risks / Trade-offs

- [Wrong merges from the dedupe key] → city is part of the key, and canonical fields are never overwritten by the second source, so a wrong merge loses a link, not data.
- [Lingua memory and start-up cost] → lazy load, four languages only. Measured: about 105 MB of heap once loaded and about 0.4 s for the first detection. The backend limit is 1Gi in prod and 768Mi in the local overlay, so no limit changes.
- [Lingua 1.2.2 is the newest release on Maven Central and dates from 2022] → the library is small and used behind one internal class (`LanguageDetector`), so replacing it touches one file. Its scores are relative to the best candidate (the best is always 1.0), so the fallback to `other` uses the gap to the runner-up, not an absolute confidence.
- [Raw payloads grow the table] → `TEXT` is TOASTed by Postgres; revisit retention when real volume is known.
- [Description text in logs] → ingest code logs ids and counts only; a test captures logs during an ingest and asserts no text appears.

## Migration Plan

Additive Liquibase changeset `003-postings.sql`; no data to migrate. Rollback drops the two tables (changesets carry `--rollback`). Deployed with the next backend image through the existing pipeline.
