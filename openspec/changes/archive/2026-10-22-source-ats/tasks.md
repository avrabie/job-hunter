# Tasks

## 1. Gates and schema

- [x] 1.1 Read Greenhouse's terms that apply to the public Job Board API (its developer documentation and website terms of use) and add a short "Terms check" note to `design.md` with the date and what they say about reading board data without an account and about reuse by third parties. Verify the note exists; if the terms forbid this use, stop and ask the user before any further task. Also record that individual employers' own terms were not reviewed one by one.
- [x] 1.2 Check from the cluster that the API answers: run a one-off pod with `curl -sI -A "JobHunter/0.1 (+https://s4v3.net)" https://boards-api.greenhouse.io/v1/boards/proton/jobs` (via `kubectl --context s4v3`, deleted afterwards) and record the status in `design.md` under Risks. Verify a 200 with an `etag` header; if it answers 403 or a challenge, stop and ask the user.
- [x] 1.3 Add `backend/src/main/resources/db/changelog/004-sources.sql` with changeset `007-source-fetch-state` (`source_fetch_state`: `source_id` primary key, `etag`, `last_run_at`) with `--rollback`, include it in `db.changelog-master.yaml`, and raise the changeset count in `DatabaseBaselineTests` to 7 with a check for the new table. Verify with a schema test and that `DatabaseBaselineTests` passes, including the "running again changes nothing" test.

## 2. robots.txt

- [x] 2.1 Implement `RobotsTxt` in `sources/internal` (the `*` group, `Allow` and `Disallow`, `*` and `$` patterns, longest match with Allow winning ties, comments and blank lines, case-insensitive field names). Verify with unit tests for each rule in the spec: allowed path, disallowed path, `Disallow: /*.txt$` against `/a/b.txt`, `/a/b.txt.html` and `/v1/boards/acme/jobs`, an empty `Disallow:`, and a file with only other agents' groups. Include Greenhouse's real rules (`Disallow: /embed/`) as a fixture and assert `/v1/boards/proton/jobs` is allowed.

## 3. Filters and place mapping

- [x] 3.1 Implement the Swiss-location filter and the city/canton mapping in `sources/internal/greenhouse`. Verify with unit tests for every spec scenario: `Zurich; `, `Geneva; Taipei; Skopje`, `Zürich, Switzerland`, `Remote - Switzerland` (kept, no city), `Warsaw`, `Berne, Indiana` and `Bernardo` (filtered out), `Paris; Geneva; Barcelona` (city Geneva, canton GE), and a location that is empty or null.
- [x] 3.2 Implement the IT-role filter with configurable keyword and exclusion lists (whole words, case-insensitive, exclusion wins). Verify with unit tests for every spec scenario: `Senior Site Reliability Engineer, Infrastructure`, `Working Student, Security Engineer`, `Head of Growth`, `Group Product Manager, B2C`, `Sales Engineer`, a custom list of only `analyst`, and `Engineering Manager, Partner Tech` (kept, through the `engineering` keyword).

## 4. Mapping

- [x] 4.1 Implement the Greenhouse list and job parsing (Jackson, unknown fields ignored, malformed JSON rejected) and the mapping to `PostingCandidate` (`greenhouse-<board>` source id, job id, URL, title, company name, city and canton, raw payload as the job's compact JSON). Verify with unit tests: a full job, a job missing id, title or URL (skipped), a job with a location of only a country, and that salary, workload and remote mode are unknown.
- [x] 4.2 Implement the description converter (decode the double-encoded HTML, `- ` lines for list entries, blank lines between blocks, tags removed, entities decoded). Verify with unit tests: the escaped-HTML example from the spec, nested lists, `<br>`, `&amp;` and `&nbsp;`, empty content (skipped), and a script tag whose text must not appear in the output.

## 5. Fetching

- [x] 5.1 Implement `PoliteHttp`: configured User-Agent, timeouts, the byte-counting body limit, the pause between requests to one host, JSON content-type and status checks, `If-None-Match`, and failure results whose messages never contain body text. Verify with tests against a local `HttpServer`: 200 with an `ETag`, 304, 404, 500, a slow response, an oversized body, an HTML body, the headers on the request, and the pause between two requests.
- [x] 5.2 Implement the fetch-state store over `source_fetch_state` (read `ETag`, record a run start, save the `ETag` only after an accepted fetch). Verify with integration tests: the `ETag` survives a new bean lookup, a failed fetch leaves it unchanged, and the minimum interval is measured from the last run start.

## 6. Crawl

- [x] 6.1 Implement the public `AtsCrawl`, `CrawlResult`, `BoardResult` and `BoardOutcome` and the crawl service: robots.txt check per host, per-board advisory try-lock, minimum-interval check, list fetch, Swiss and IT filters, detail fetch per kept job, ingest through `PostingIngest` with each job isolated, and per-board counts. Verify with integration tests against a local server for every scenario in the spec: first run (3 created, 2 filtered out), second run with the same content (3 unchanged, no new rows), one changed job (1 updated), a detail request answering 500 (skipped, others ingested), an unknown board (failed, next board crawled), robots.txt disallowing the board (blocked), `robots.txt` answering 500 (failed), 304 (not modified, no detail request), too soon, an empty board list (no request), and an HTML body (failed, nothing ingested).
- [x] 6.2 Verify that two crawls started at the same moment crawl each board once and report the other as already running, and that the local server saw one list request per board (a test with a latch, in the style of `PostingIngestTests`).
- [x] 6.3 Add the opt-in startup runner behind `jobhunter.sources.greenhouse.run-on-startup` (default off), the default settings with the seed boards, and log only outcomes and counts. Verify with tests that a default start makes no request and that an enabled start makes exactly one crawl.
- [x] 6.4 Add a log-capture test, in the style of `PostingLoggingTests`, with a marker in a job's title and content and in a failing response body, asserting the marker never reaches the logs during completed, skipped-job and failed runs.
- [x] 6.5 Verify `ModularityTests` passes with the new `sources -> postings` dependency, and that nothing outside `sources` imports `sources.internal`.

## 7. Docs and integration

- [x] 7.1 Document the settings, the seed boards (with the date they were checked), how to add a board, the stopgap startup run, and the adapter's limits (no salary, workload or remote mode) in `backend/README.md`, and update the `sources` row in `PLAN.md`. Verify the setting names in the README match the code.
- [x] 7.2 Run the full backend build (`./gradlew build` in `backend/`) and verify it passes.

## Workflow follow-up

- Build and push the backend image, bump the tag in `k8s/overlays/prod/kustomization.yaml`, preview with `kubectl --context s4v3 diff -k k8s/overlays/prod`, apply, and confirm `source_fetch_state` exists.
- With the user's go-ahead, enable the startup run for one deploy, check the logged per-board counts and the `posting` row count on s4v3.net, then switch it off again.
- Archive the change, then propose `ingestion-scheduler`.
