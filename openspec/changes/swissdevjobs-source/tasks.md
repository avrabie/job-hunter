# Tasks

## 1. Adapter (sources)

- [x] 1.1 `SourcesSettings.swissdevjobs` (`enabled` default true, `baseUrl`) and `application.yaml`. Verify: settings test for defaults and override.
- [x] 1.2 List and feed parsing (JSON list; XML feed with DTDs and external entities disabled, size cap) and the slug join. Verify: unit tests with a fixture taken from the real shapes: full entry, no salary, one salary amount, unknown workplace, paused, no matching feed item, empty description, entity in the feed, malformed list, malformed feed.
- [x] 1.3 Mapping to `PostingCandidate` (URL without tracking, city/canton, remote mode, salary, plain-text description, raw payload). Verify: mapping unit tests including the URL form and that the apply link is not stored.
- [x] 1.4 `SwissDevJobsCrawl`: advisory lock per source, minimum interval, robots.txt for the list and feed paths, ETag on the list, list then feed, ingest, counts, `retryLater` keeps the validator unset, log lines with counts only. Verify: integration tests against a fake server (like `FakeGreenhouse`): first run, second run unchanged, 304, blocked by robots.txt, unreachable, partner ad skipped and retried, dedupe with a Greenhouse posting, logging marker test.

## 2. One crawl for all sources

- [x] 2.1 Combined crawl implementing `AtsCrawl` that runs Greenhouse then SwissDevJobs and isolates failures; scheduler and startup run keep working unchanged. Verify: tests for both sources, Greenhouse failing, SwissDevJobs throwing, adapter disabled; existing `CrawlSchedulerWiringTests`, `StartupCrawlTests` and `GreenhouseCrawlTests` still pass.

## 3. Docs and checks

- [x] 3.1 Update `sources/README.md` and the sources/postings rows in PLAN.md (second source built; jobs.ch, jobup.ch, Job-Room not crawled and why). Verify: wording matches the code.
- [x] 3.2 `./gradlew test integrationTest` including `ModularityTests`; `openspec validate swissdevjobs-source`. Verify: all green.
- [ ] 3.3 Deploy the backend only after the owner says so; check the first run on prod by the crawl log line (counts only); archive after the owner checks the postings on prod.
