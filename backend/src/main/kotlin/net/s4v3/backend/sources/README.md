# sources

Purpose: fetch job postings from outside boards and hand them to `postings`. Two sources: Greenhouse boards and SwissDevJobs. Respects robots.txt, per-host pauses and a minimum interval per board or source. jobs.ch and jobup.ch are not crawled (their `robots.txt` disallows `/api/` and the job pages for everyone) and Job-Room has no public read API.

## Public API (this package)
- `AtsCrawl.run()` returns `CrawlResult` (one `BoardResult` per Greenhouse board, then one for `swissdevjobs`, with `BoardOutcome`: `COMPLETED`, `NOT_MODIFIED`, `TOO_SOON`, `BLOCKED_BY_ROBOTS`, `FAILED`, `ALREADY_RUNNING`).

## Internal (`internal/`)
- `GreenhouseCrawl`: the adapter; advisory lock per board, robots, ETag list fetch, then detail per Swiss IT job, then `PostingIngest`.
- `SwissDevJobsCrawl`: source id `swissdevjobs`; advisory lock, minimum interval, robots for `/api/jobsLight` and `/rss`, ETag on the list (kept only if every job had a description), then the feed; the two are joined by slug, jobs without a feed text are skipped and retried next run, paused jobs are filtered out. Empty result when `jobhunter.sources.swissdevjobs.enabled` is false.
- `AllSourcesCrawl` (`@Primary` `AtsCrawl`, used by the scheduler and the startup run): runs `GreenhouseCrawl`, then `SwissDevJobsCrawl`; a source that throws becomes a failed result and does not stop the other.
- `swissdevjobs/SwissDevJobsMapping`: JSON list and XML feed parsing (no DTDs or external entities) and the mapping to `PostingCandidate`; description text uses `DescriptionText` from `greenhouse/`.
- `greenhouse/GreenhouseMapping`, `greenhouse/Filters` (`SwissLocation`, `RoleFilter`).
- `PoliteHttp`, `RobotsTxt`, `FetchStateStore`.
- `CrawlScheduler` (polls every 30 s; `SchedulingConfig` holds `@EnableScheduling` for the whole app), `ScheduleStore`, `ScheduleRules`.
- `CrawlScheduleController`: `GET`/`POST /api/admin/crawl-schedule` (ADMIN).
- `StartupCrawl`: optional one run after startup. `SourcesSettings`.

## Events
- None. It calls `PostingIngest` directly; the event comes from `postings`.

## Tables
- `source_fetch_state` (`004-sources.sql`), `crawl_schedule` (`005-crawl-schedule.sql`).

## Settings
- `jobhunter.sources.*` including `schedule.*`, `greenhouse.*` and `swissdevjobs.*` (`enabled`, default true; `base-url`).

## Calls
- `accounts`, `postings`. HTTP out to Greenhouse and SwissDevJobs.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `GreenhouseCrawlTests` (with `FakeGreenhouse`), `SwissDevJobsCrawlTests` (with `FakeSwissDevJobs`), `AllSourcesCrawlTests`, `CrawlScheduleApiTests`, `CrawlSchedulerWiringTests`, `StartupCrawlTests`, `ScheduleStoreTests`, `FetchStateStoreTests`, `SourcesLoggingTests`.
- Unit: `CrawlSchedulerTests`, `ScheduleRulesTests`, `RobotsTxtTests`, `PoliteHttpTests`, `GreenhouseMappingTests`, `SwissDevJobsMappingTests`, `SwissDevJobsSettingsTests`, `GreenhouseFilterTests`.

Read on 2026-10-26 from: sources/AtsCrawl.kt, sources/internal/*.kt (GreenhouseCrawl, CrawlScheduler, CrawlScheduleController, StartupCrawl, SourcesSettings, ScheduleStore), db/changelog/004-005, test file names.
