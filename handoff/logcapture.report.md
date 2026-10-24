# Log capture helper (flaky task 1.1 follow-up), backend tests only

Not committed. No main code, `build.gradle.kts` or container configuration touched.

## Helper API (`backend/src/test/kotlin/net/s4v3/backend/LogCapture.kt`)
- `class LogCapture(logger: Logger) : AutoCloseable`
  - the constructor attaches a thread-safe appender (`AppenderBase` over `CopyOnWriteArrayList`) to `logger`
    (the root logger in most tests, `ours`/`net.s4v3.backend` in two)
  - `events(): List<ILoggingEvent>`: a snapshot of what has been captured so far
  - `close()`: detaches and stops the appender (use in `finally`, or from `@AfterEach`)
- `fun ILoggingEvent.fullText(): String`: `formattedMessage` plus the throwable's stack trace when present
  (the text the privacy checks used before)

Tests keep their own level handling (`root.level = Level.TRACE` and restore), so the helper does not change levels.

## Helper test (`LogCaptureTests.kt`)
`concurrent writers lose no event while a reader takes snapshots`: 8 writer tasks log 200 INFO lines each on one logger, a
reader task loops over `events()` and `fullText()` until the writers finish, then the test asserts 1600 marked events and
no exception from any future.

## Changed classes (all under `backend/src/test/kotlin/net/s4v3/backend/`, only log-capture lines and imports)
1. ProfileContactLoggingTests.kt
2. PostingsLoggingTests.kt
3. ProfileLoggingTests.kt
4. TokenLoggingTests.kt
5. FilesLoggingTests.kt
6. ProjectAnalysisApiTests.kt
7. CvExtractApiTests.kt
8. CvExportApiTests.kt
9. MatchingLoggingTests.kt
10. PostingLoggingTests.kt
11. SourcesLoggingTests.kt
12. CrawlScheduleApiTests.kt
13. CrawlSchedulerTests.kt (field appender moved to `private val capture = LogCapture(logger)`, attach happens at construction,
    `@AfterEach` closes it; the `@BeforeEach` now only sets the level; the function was renamed `capture()` to `setLevel()`)
14. llm/LlmLoggingTests.kt (also imports `net.s4v3.backend.LogCapture` and `net.s4v3.backend.fullText`)

Each replaced `ListAppender` line became `val capture = LogCapture(<logger>)` at the place of the old declaration, the
`addAppender` line is gone, `detachAppender(appender)` became `capture.close()`, `appender.list` became `capture.events()`, and the
three-line message expression became `event.fullText()`. No assertion, marker or "capture is alive" check was changed or removed.
New file: `LogCapture.kt`, `LogCaptureTests.kt`. Grep check: no `ListAppender`, `addAppender` or `.list` on an appender left in
`backend/src/test`.

## Verify numbers
- Compile error on the first try (LlmLoggingTests is in package `llm`, missing imports): fixed, not a test failure.
- Run 2 (after the fix): `./gradlew test --offline` with 15 `--tests` filters in one invocation (the 14 classes plus
  `LogCaptureTests`), BUILD SUCCESSFUL, exit 0, 1m 8s. Per class from JUnit XML, all 0 failures, 0 errors, 0 skipped:
  PostingLoggingTests 1, PostingsLoggingTests 1, ProfileContactLoggingTests 1, ProfileLoggingTests 1, TokenLoggingTests 1,
  FilesLoggingTests 1, MatchingLoggingTests 1, SourcesLoggingTests 1, LlmLoggingTests 1, ProjectAnalysisApiTests 9,
  CvExtractApiTests 17, CvExportApiTests 14, CrawlScheduleApiTests 9, CrawlSchedulerTests 8, LogCaptureTests 1.
  (One Gradle invocation instead of 14 separate ones, to save Gradle startups; each class ran once.)
- Loop: `./gradlew cleanTest test --offline` for `PostingLoggingTests` and `LogCaptureTests`, 10 times: 10 of 10 exit 0,
  each log shows the test task executed (`cleanTest` forces the run, Gradle would otherwise reuse up-to-date results).

## Notes
- The helper makes snapshots thread-safe but does not wait for async listeners. The tests still read after `close()`, so an
  event logged by a listener thread after detach is not seen by the privacy checks. That is the weakness flaky.report.md
  mentions; fixing it needs an await before close (out of scope here, test logic unchanged as asked).
- The `CrawlSchedulerTests` field-to-constructor change is the only lifecycle change; the other 12 keep their try/finally.
