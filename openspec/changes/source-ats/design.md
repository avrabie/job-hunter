# Design

## Context

`postings-core` provides `PostingIngest` and `PostingCandidate`. The `sources` module is an empty package with an `internal` subpackage. The backend has Spring's `RestClient` (webmvc starter), Jackson (`tools.jackson`), JDBC, Liquibase files `001`-`003` with changeset ids up to `006`, and tests that start local HTTP servers (`MockIssuer`). Network policies restrict only ingress, so outbound HTTPS needs no policy change. See proposal.md for the API facts this design rests on. The abandoned `source-swissdevjobs` change worked out the generic parts (politeness, robots.txt, fetch state, lock, startup run); this design reuses them.

## Goals / Non-Goals

**Goals:**
- A Greenhouse adapter that produces only Swiss IT postings and is cheap for the boards it reads.
- Reusable plain classes (robots.txt evaluator, polite fetcher, fetch-state store, lock) that the next adapter (Lever) can use unchanged.

**Non-Goals:**
- Scheduling, retries across runs, or any cron.
- A generic adapter framework. An interface is introduced when the second adapter shows what it needs.
- Perfect IT classification. The title filter is a heuristic; it is configurable and its misses show up in the filtered-out count.

## Decisions

### Package layout and boundary
Public API in `net.s4v3.backend.sources`: `AtsCrawl.run(): CrawlResult` plus `CrawlResult`, `BoardResult`, `BoardOutcome`. Everything else is in `sources.internal` (and `sources.internal.greenhouse` for the API-specific parts). `sources` depends only on the `postings` public API. No change to `postings`.

### Light list first, details only for kept jobs
`GET /v1/boards/{token}/jobs` (no `content`) returns id, title, location, URL, `updated_at` per job; it is small and carries an `ETag`. After filtering, `GET /v1/boards/{token}/jobs/{id}` is called for each kept job. Sequential, with the pause.

- *Alternative:* `content=true` on the list. One request per board, but Databricks answers 9.9 MB for 899 jobs of which 9 are Swiss; that is wasteful for them and for us.
- *Alternative:* skip details of jobs whose `updated_at` is unchanged. It saves requests but leaves `last_seen` stale on those postings, which a later expiry feature would misread; revisit when expiry is built. With about 60 kept jobs across the seed boards, a run is about 70 requests at one per second.

### Source ids: one per board
`sourceId = "greenhouse-" + token`, `sourcePostingId = job id`. Greenhouse job ids are unique within a board, and this keeps the `postings` unique key (source id, source posting id) simple. Cross-board and cross-source duplicates are left to the posting store's dedupe (company, title, city).

### Filters
- **Swiss:** a regex of Swiss place names and `switzerland`/`schweiz`/`suisse`/`svizzera` with word boundaries, applied to the location text. This rejects `Berne, Indiana` and `Bernardo`. The first matching known city (in text order) gives city and canton from a small built-in map (Zurich/Zürich/Winterthur ZH, Geneva/Genève GE, Basel BS, Bern BE, Lausanne VD, Zug ZG, Lucerne/Luzern LU, St. Gallen SG, Lugano TI, and a few more); country-only text gives no city.
- **IT role:** keyword lists in settings (`jobhunter.sources.greenhouse.it-keywords`, `...excluded-keywords`), matched on the title as whole words, case-insensitive; exclusion wins. Defaults as in the spec; they include `engineering` because `engineer` does not match inside it as a whole word, and `Engineering Manager` roles are wanted.
- *Alternative:* use Greenhouse departments. They are company-specific (`CC - Infrastructure - Storage`, `Field Engineering`) and cannot be mapped without a per-company table.
- *Alternative:* ask Claude to classify. Costs money and adds a dependency on `llm` for a task a keyword list does well enough; revisit if the filter proves too coarse.
- *Known blind spots:* `Solutions Architect` and `Forward Deployed Engineer` roles at product companies are pre-sales or consulting but pass; `Product Manager` is excluded though some users may want it. Settings, not code, change this.

### Settings
`jobhunter.sources.user-agent` (default `JobHunter/0.1 (+https://s4v3.net)`), `jobhunter.sources.pause` (`PT1S`), `jobhunter.sources.min-interval` (`PT15M`), `jobhunter.sources.max-body-bytes` (8 MB list, 1 MB job), timeouts (5 s connect, 30 s read), `jobhunter.sources.greenhouse.base-url` (default `https://boards-api.greenhouse.io`), `...boards` (the token list), the two keyword lists, and `...run-on-startup` (`false`). The base URL setting lets tests point at a local server without test-only code paths.

### HTTP with `RestClient` on the JDK client
The JDK `HttpClient` behind `RestClient` gives timeouts and streaming. The body is read through a counting stream that stops at the limit. A small `PoliteHttp` class owns the User-Agent, the pause (a per-host "next allowed time") and the size limit, and is what future adapters use.

### Conditional fetch state in the database
Table `source_fetch_state(source_id PK, etag, last_run_at)`, changeset `007` in `004-sources.sql`; the key is the per-board source id. The `ETag` is written only after an accepted list fetch; `last_run_at` is written when a board's run starts, so the minimum interval holds after a crash. The table holds no personal data and no posting text.

- *Why the list `ETag` is enough:* a `304` means no job on the board changed, so no details are fetched. If the list changed, details of all kept jobs are fetched (see above).

### Run lock
`pg_try_advisory_xact_lock` on a key derived from the board's source id, held in a transaction that spans that board's crawl. A second caller gets `false` and reports `ALREADY_RUNNING` for that board. This works across replicas and needs no new table. Each `PostingIngest.ingest` call runs in its own transaction, as `postings` defines it, so one bad job cannot roll back the board; the lock transaction uses its own `TransactionTemplate`.

- *Alternative:* ShedLock. It belongs with `ingestion-scheduler`; this lock is the minimum so manual and startup runs cannot overlap.
- *Known limit:* sources must run one after another (`postings-core` design: the cross-source dedupe key is not locked). A crawl runs boards sequentially, so within this adapter that holds; the scheduler must not run two source types at once.

### robots.txt evaluation
A small `RobotsTxt` class: parse the `User-agent: *` group, evaluate `Allow` and `Disallow` with `*` and `$` patterns by longest match (Allow wins ties), as in RFC 9309. It is fetched once per host per crawl and not cached across crawls. 404 means allowed; 5xx or timeout means fail that host's boards.

### JSON parsing
Jackson (`tools.jackson`) into small private data classes with unknown fields ignored. The detail JSON is kept as the raw payload by re-serializing the parsed tree of that job (compact), not byte-for-byte; enough to re-parse, and `postings` stores whatever string it receives.

### Description text
The detail's `content` is HTML escaped as text inside JSON (`&lt;p&gt;...`). The converter first unescapes entities once (Spring's `HtmlUtils.htmlUnescape`) to get the HTML, then removes tags: `<li>` becomes a `- ` line, block tags (`p`, `div`, `h1`-`h6`, `br`, `ul`) become line breaks, then entities are decoded again for the text. Two passes are needed because the API double-encodes. No HTML parser dependency; the markup is ordinary employer HTML, and a stray tag in the output is a cosmetic flaw, not a failure.

- *Alternative:* jsoup. More robust for arbitrary employer HTML (nested tables, scripts). Reconsider if descriptions come out poorly in the first real crawl.

### Opt-in startup run
An `ApplicationRunner` calls `AtsCrawl.run()` only when `run-on-startup` is true and logs per-board outcomes and counts. It is a stopgap so the first crawl can be checked on s4v3.net by setting one environment variable; `ingestion-scheduler` replaces it as the normal trigger and may remove the setting. It adds no HTTP endpoint.

### Seed boards
`proton`, `getyourguide`, `scandit`, `databricks`, `elastic`, `mongodb`, `gitlab`, `datadog`, `grafanalabs`, `ripple`: each answered 200 and had at least one Swiss role on 2026-10-22. They go in the committed default configuration. Adding a board means adding its token to the list (and checking it with one request first); the list lives in the application settings, so a deployment can override it without a code change.

### Test fixtures
Tests use a local `com.sun.net.httpserver.HttpServer` serving synthetic boards written for the tests, not copies of real job ads.

### Module boundaries and database
New table only; `source_fetch_state` is owned by `sources`. `ApplicationModules.verify()` must pass with the new `sources -> postings` dependency.

## Risks / Trade-offs

- [An employer's ad text is copyrighted and Greenhouse's docs are silent on third-party reuse] → we read only what the employer publishes through the documented public API, store it for matching, and link every posting to the original ad; the boards list is small and hand-picked, and any employer can be dropped by removing its token. Noted in the terms check (task 1.1).
- [Greenhouse or an employer blocks or rate-limits us] → sequential requests with a pause, conditional GET, a 15-minute minimum interval, and an identifying User-Agent; a block shows up as a failed board, not as a retry storm.
- [The IT title filter drops real IT roles or keeps others] → the counts per run show filtered-out jobs; both keyword lists are settings.
- [Location text is free form] → unknown text means the job is filtered out (a missed Swiss job is cheaper than a wrong one); the place list can grow.
- [The lock transaction holds a connection for a whole board crawl, up to about a minute] → at most one connection per board crawl, run rarely; the pool is not under pressure from this job.
- [Boards removed or renamed by the employer] → a 404 is reported as that board failing; the list is edited by hand.

## Migration Plan

Additive changeset `004-sources.sql` (`007`). Deploy the new backend image with the run-on-startup setting off. To check the first crawl, set the setting for one deploy, read the logged per-board counts and the `posting` row count, then unset it. Rollback: redeploy the previous image; the table can stay.

## Open Questions

- None that change the specs or the task list. Which further employers to add is an ongoing, hand-maintained choice, not a design question.
