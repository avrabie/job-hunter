# sources

Purpose: fetch job postings from outside boards and hand them to `postings`. Today only Greenhouse boards. Respects robots.txt, per-host pauses and a minimum interval per board.

## Public API (this package)
- `AtsCrawl.run()` returns `CrawlResult` (one `BoardResult` per board, with `BoardOutcome`: `COMPLETED`, `NOT_MODIFIED`, `TOO_SOON`, `BLOCKED_BY_ROBOTS`, `FAILED`, `ALREADY_RUNNING`).

## Internal (`internal/`)
- `GreenhouseCrawl`: the adapter; advisory lock per board, robots, ETag list fetch, then detail per Swiss IT job, then `PostingIngest`.
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
- `jobhunter.sources.*` including `schedule.*` and `greenhouse.*`.

## Calls
- `accounts`, `postings`. HTTP out to Greenhouse.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `GreenhouseCrawlTests` (with `FakeGreenhouse`), `CrawlScheduleApiTests`, `CrawlSchedulerWiringTests`, `StartupCrawlTests`, `ScheduleStoreTests`, `FetchStateStoreTests`, `SourcesLoggingTests`.
- Unit: `CrawlSchedulerTests`, `ScheduleRulesTests`, `RobotsTxtTests`, `PoliteHttpTests`, `GreenhouseMappingTests`, `GreenhouseFilterTests`.

Read on 2026-10-24 from: sources/AtsCrawl.kt, sources/internal/*.kt (GreenhouseCrawl, CrawlScheduler, CrawlScheduleController, StartupCrawl, SourcesSettings, ScheduleStore), db/changelog/004-005, test file names.
