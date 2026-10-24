# Flaky test report (task 1.1), backend, 573 tests, profile `test`, Docker Desktop, 8 CPUs

Code was not edited. All runs `./gradlew clean test` (the repo has no `verify` task yet); runs 6 and 7 with four `yes > /dev/null`
processes running on the 8 cores (load was moderate, not saturating). Run 2 also had the cache DEBUG logging via a temporary
init script (`-I`, outside the repo).

| run | load | wall | result |
|---|---|---|---|
| 1 | no | 116 s | pass (573) |
| 2 | no (+cache DEBUG log) | 151 s | 9 failures, all `ProjectAnalysisApiTests` |
| 3 | no | 157 s | pass |
| 4 | no | 122 s | 1 failure `PostingLoggingTests` |
| 5 | no | 91 s | pass |
| 6 | yes | 118 s | pass |
| 7 | yes | 123 s | pass |
So 2 of 7 runs failed, both with a different test, neither related to the load.

## Failures
| test | run | message (first line) | likely cause | proposed fix |
|---|---|---|---|---|
| `ProjectAnalysisApiTests` (all 9 tests) | 2 | `ParameterResolutionException: Failed to resolve parameter MockMvc mvc` caused by `Failed to load ApplicationContext ... PSQLException: The connection attempt failed ... SocketTimeoutException: Read timed out` (Liquibase) | Testcontainers race: the container was reported started 1.1 s after creation (log "started in PT1.07S", port 55273) but Postgres was not yet answering; the JDBC connection was accepted by the Docker port proxy and then read-timed-out (about 50 s). Spring then caches the failure (`failureCount = 1`, "failure threshold (1) exceeded") so every remaining test of the class fails at once, 1 flake = 9 red tests | give the container an explicit wait: `.waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))` or `Wait.forSuccessfulCommand("pg_isready")` plus `withStartupTimeout`; and add `?connectTimeout=10&socketTimeout=30` to the JDBC URL; with a single shared container (see slow.report.md) the race happens once per JVM, not 20 times |
| `PostingLoggingTests` "posting text and payloads never reach the logs ..." | 4 | `java.util.ConcurrentModificationException` at `PostingLoggingTests.kt:59` (`appender.list.filter`) | the test attaches a Logback `ListAppender` (its list is a plain `ArrayList`) to the ROOT logger at TRACE, and iterates it after `detachAppender`; other threads log into the root logger at the same time (async `@ApplicationModuleListener` matching of the ingested posting, scheduled-crawl thread, Hikari housekeeper), so an event is appended during iteration | copy before reading: `val events = synchronized(appender.list) { appender.list.toList() }` is not enough (ListAppender does not lock); use a thread-safe appender (a small `AppenderBase` over `CopyOnWriteArrayList`, one shared helper) and wait for the async listeners to finish (`await` until `posting_match` rows exist or the event publication is completed) before detaching |

The same `ListAppender`-on-root pattern is used by 14 classes and can fail the same way: PostingsLoggingTests,
PostingLoggingTests, ProfileContactLoggingTests, ProfileLoggingTests, TokenLoggingTests, FilesLoggingTests, MatchingLoggingTests,
SourcesLoggingTests, LlmLoggingTests, ProjectAnalysisApiTests, CvExtractApiTests, CvExportApiTests, CrawlScheduleApiTests,
CrawlSchedulerTests. One helper (`CapturedLogs`) fixes all of them. These tests also read only after detaching, so a late event
is missed by the privacy check, which makes the check slightly weaker than it looks.

## Scan of the ten classes with sleep/delay/await (not failing, risk assessed by reading)
| class | assumption | risk |
|---|---|---|
| `CrawlSchedulerWiringTests` | "nothing crawls while off" sleeps 2.5 s, clears the request log, then expects 0 requests; a crawl from the other test (cron every 3 s, 1 s tick) is assumed to be over in 2.5 s; the other test waits up to 20 s for a tick. Order of the two tests decides what is "left over" | medium: a crawl slowed by load past 2.5 s lands in the empty window; fix: stop the schedule (`delete from crawl_schedule`) and wait until `ScheduleStore`/scheduler reports no crawl running instead of a time |
| `StartupCrawlEnabledTests` | waits for the first request (up to 15 s), then 500 ms, then expects exactly 1; a second crawl later would be missed, a slow start is fine | low |
| `FilesApiTests` | ticket expiry `ticket-seconds=2`, sleeps 2.1 s: 100 ms margin; two `sleep(5)` to get ordered `created_at` | medium: under load the ticket is still valid only if the sleep returns early, which cannot happen, so the real risk is the reverse (a ticket expiring before it is first used on a slow run: the test's earlier `plain(...)` calls with a 2 s ticket); sleep(5) ordering may tie on timestamp granularity: use explicit timestamps |
| `PoliteHttpTests`, `GitHubRepositoryProviderTests`, `GitHubDigestProviderTests` | real HTTP on localhost with a 2 s read timeout for normal answers and a 300 ms timeout against a 1.5 s delay; "gap >= 350 ms" for a 400 ms pause | low to medium: a GC or CPU stall above 2 s on a normal answer fails it; use 5 s for the normal timeout, keep the short one |
| `PostingIngestTests`, `EventPublicationRegistryTests` | awaitility 10 s on async listeners | low; fine unless the machine stalls over 10 s |
| `MatchingFlowTests`, `MatchingLoggingTests`, `MatchingTestSupport.await` | polling every 50 ms up to 10 s for async match rows; `sleep(20)` between two postings to order them | low; ordering by 20 ms sleep may tie on a coarse clock: low risk on this JVM |
| `PostingSearchTests`, `PostingsApiTests` | `sleep(20)` to order `created_at` | low, same ordering note |
| `PdfRendererTests` | `sleep(300)` then asserts that no outside request was made (negative check) | low risk of a false failure, but the check could pass before a late request arrives; wait on a latch |
| `ProfileServiceTests`, `ConcurrentProvisioningTests`, `GreenhouseCrawlTests` | latches with 10 s limits, real DB races; "exactly one wins" assertions depend on the database constraints, not on timing | low |
Not timing but state: tests that count rows (`postingRows(company)`) use unique names or subs; none is order dependent in the five runs.
`CvExportApiTests` busy test acquires the guard permits directly, so it has no timing risk (only the 5 s of slow.report.md).
