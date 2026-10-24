# Timing fixes (fast-build-and-code-map, design decisions 3 and 4)

Other agents ran Gradle at the same time, so class times are noisy; per-test times come from the XML where they matter.
Each fix was run 10 times with `./gradlew test --offline --tests "<Class>" --rerun`. Not committed.

## 1. CvExportApiTests busy test (real 5 s wait)
- Main (seam): `cv/export/internal/DefaultExportService.kt` (`RenderGuard(settings: ExportSettings)`, `wait = settings.renderWait`),
  `cv/internal/ExportSettings.kt` (`renderWait: Duration = 5s`), `application.yaml` (`jobhunter.cv-export.render-wait: 5s`, documented).
  Production default unchanged. Spec behaviour kept (third render waits, then 503 `busy`).
- Test: `CvExportApiTests.kt` adds `jobhunter.cv-export.render-wait=200ms` to the existing `@TestPropertySource` (no new context).
- Before: busy test about 5 s (class 7.9 s quiet). After: busy test 0.33 s. 10 of 10 runs passed (14 tests).

## 2. CrawlSchedulerWiringTests (5 s sleeps, 2.5 s windows)
- Test only, no main change. The "off" test now runs first (`@TestMethodOrder(OrderAnnotation)`, `@Order(1)` and `(2)`), so no crawl
  of the other test can be left over; the two 2.5 s sleeps became one 1.5 s window (1.5 ticks of PT1S). The second test uses cron
  `* * * * * *` (was every 3 s) and a 10 ms poll with a 10 s limit (was 100 ms, 20 s).
- Before: class 9.8 s. After: the two tests take 2.0 s each. 10 of 10 runs passed.
- Log noise: a `GreenhouseCrawl ... FAILED reason=unexpected PSQLException` line appears at the end of each run (a crawl still
  running while the context shuts down; existing behaviour, not an assertion; not touched).

## 3. FilesApiTests ticket expiry (2 s ticket, 2.1 s sleep, 100 ms margin)
- Main (seam, minimal): `files/internal/DownloadTickets.kt` takes `clock: Clock = Clock.systemUTC()` and uses `clock.instant()`
  instead of `Instant.now()`. No Clock bean exists in production, so Spring uses the Kotlin default (the passing context start shows it).
- Test: `FilesApiTests.kt` imports a nested `@TestConfiguration` with a hand-moved `TestClock` bean; the expiry test calls
  `clock.advance(ticketSeconds + 1)` instead of sleeping. The `ticket-seconds=2` property is removed (default 60 s), so a slow run
  can no longer expire a ticket before its first use either.
- Before: 2.1 s sleep (class 4.5 s quiet). After: no sleep. 10 of 10 passed (28 tests). In the loop, run 6 printed no BUILD line
  (probably a clash with another agent's Gradle); an extra rerun passed, so there are 10 clean passes.

## 4. PdfRendererTests `no network request is made`
- Test only: `sleep(300)` removed. `render()` is synchronous and a fetch is answered and counted by the fake server before it
  returns, so `assertEquals(0, hits.get())` right after the render is a proof. Comment added. 10 of 10 passed; class about 3 s.

## 5. Left alone (reason)
- `FilesApiTests` `sleep(5)` x2, `PostingsApiTests`, `MatchingFlowTests`, `PostingSearchTests` `sleep(20)`: they order `created_at`;
  low risk on this JVM; a real fix needs explicit timestamps through the repositories (larger change).
- `StartupCrawlTests` `sleep(500)`: a negative window ("exactly one crawl"); cannot be event-based without a main seam.
- `MatchingTestSupport` 50 ms poll with a 10 s limit, awaitility in `PostingIngestTests` and `EventPublicationRegistryTests`:
  already bounded polling.
- `PoliteHttpTests`, `GitHubDigestProviderTests`, `GitHubRepositoryProviderTests`: the server delays are the test subject.
  Raising the normal 2 s timeout to 5 s and the delay to 600 ms (flaky and slow reports) was not done: not a clear-cause small change
  without reading the providers; suggested follow-up.
- No edits to `build.gradle.kts`, `TestcontainersConfiguration`, `IntegrationTest.kt` or log-capture code.
