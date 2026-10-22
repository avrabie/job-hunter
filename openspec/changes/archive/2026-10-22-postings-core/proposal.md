# Proposal

## Why

Every later phase (matching, drafting, digest) starts from job postings, but the `postings` module is an empty package. Before any source adapter or scheduler can be built, the system needs one normalized posting model, shared storage with dedupe, and language detection, so adapters only have to translate a site's payload into that model.

## What Changes

- Add the normalized `JobPosting` model: title, company, description, canton/city, workload range, remote/hybrid, salary in CHF when present, posting language, source reference, and timestamps.
- Add shared posting storage (Liquibase changeset) that keeps the raw source payload next to the normalized record so postings can be re-parsed later.
- Add idempotent ingest in the `postings` public API (`PostingIngest`): a source adapter hands over a candidate posting and gets back created, updated, or unchanged.
- Add dedupe: the same posting re-crawled from the same source updates the existing row; the same posting seen on a second source is linked to one canonical row instead of stored twice.
- Add language detection (EN, DE, FR, IT, other) on ingest. French and Italian postings are stored and flagged out of scope for drafting, not dropped.
- Publish a `JobIngested` event (id only) after a posting is created, so matching can later react to it.
- Add a read API inside the backend (`PostingQuery`) for other modules; no REST endpoint yet.

### Out of scope

- Any `sources` adapter, robots.txt handling, rate limiting, and scheduling (separate changes: `source-<ats>`, `ingestion-scheduler`).
- Embeddings and the pgvector prefilter (matching phase).
- REST endpoints and UI for postings.
- Expiry or closing of postings that disappear from a source.
- Per-user data of any kind: postings are shared, and this change adds no per-user tables.

## Capabilities

### New Capabilities
- `postings`: normalized shared job postings, idempotent ingest with dedupe, raw payload retention, language detection, and the `JobIngested` event contract.

### Modified Capabilities

None. `module-boundaries` already lists `postings` and is unchanged.

## Impact

- **Modules:** `postings` (new code), `sources` will depend on `postings` through its public API in a later change. No change to other modules.
- **Database:** new tables for postings and their source references, added by a new Liquibase changeset (`003-postings.sql`) and registered in the master changelog.
- **Dependencies:** one language-detection library, chosen and pinned in the design.
- **Personal data:** none. Postings are public job ads and are shared by all users; there is no per-user scoping in this change. Company contact names that appear in ad text are kept only inside the stored posting text and raw payload and never written to logs.
- **Claude cost:** none. Nothing in this change calls an LLM.
