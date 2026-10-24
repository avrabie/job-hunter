# Infra report (tasks 3.1 and the infrastructure part of 3.3), backend

## Result
- 3.1 done and ticked. Three `./gradlew clean verify` runs: 1m22s, 1m23s, 1m23s wall, all green, no failure (run 1 with cache DEBUG via env var).
- `./gradlew clean build` (fast tier): 293 tests, 30 classes, 23 s wall, green (no Docker needed).
- `./gradlew integrationTest`: 280 tests, 59 classes, 1m04s. 293 + 280 = 573, same as before.
- Before (flaky.report.md): 116 s typical, 2 of 7 runs red. Now about 83 s, 3 of 3 green.

## Files changed (backend only, nothing committed)
- `backend/build.gradle.kts`: `test` excludes tag `integration`; `integrationTest` (Test, same classes/classpath, includeTags, shouldRunAfter(test), maxParallelForks 1); `verify` lifecycle task (group verification) depends on both. `check`/`build` unchanged, so they run `test` only.
- `src/test/.../TestcontainersConfiguration.kt`: new `object SharedPostgres`, bean now returns it.
- `src/test/.../IntegrationTest.kt`: `@Tag("integration")` (covers every `@IntegrationTest` and `@ApiTest` class, 55 classes).
- `@Tag("integration")` added by hand to: `VirtualThreadTests` (own @SpringBootTest), `FailingMigrationTests`, `IssuerSettingTests`, `EventPublicationRegistryTests`, `DatabaseOutageHealthTests` (the four SpringApplicationBuilder classes, plus VirtualThreadTests).
- `EventPublicationRegistryTests.kt`, `DatabaseOutageHealthTests.kt`: use the container helpers.
- `FilesApiTests.kt`, `CvExportApiTests.kt`: own `@TestPropertySource` removed.
- `src/test/resources/application-test.properties`: three properties added.
- `openspec/changes/fast-build-and-code-map/tasks.md`: 3.1 ticked. `handoff/infra.report.md`.

## Tiers
- Integration (59 classes, 280 tests): all `@IntegrationTest`/`@ApiTest` classes, VirtualThreadTests, FailingMigrationTests, IssuerSettingTests, EventPublicationRegistryTests, DatabaseOutageHealthTests.
- Fast (30 classes, 293 tests): the rest (pure unit tests, fake HTTP servers, Koog fakes, renderers, ArchUnit/Modulith structure tests, FileStorageTests, etc). Full list in `build/test-results/test`.
- Not tagged: no class outside these starts a context or container (grep checked for SpringBootTest, WebMvcTest, DataJdbcTest, SpringApplicationBuilder, Container, ContextConfiguration).

## Container
- `SharedPostgres.instance`: one lazy static pgvector container for the JVM, `withReuse(false)`, wait for the 2nd "ready to accept connections" log line, `withStartupTimeout(2 min)`, then a loop (60 s) until a real JDBC connection runs `select 1`, JDBC URL param `connectTimeout=10`. Exposed through the existing `@ServiceConnection` bean.
- Two traps found and fixed: (1) closing any cached context stopped the shared container (Boot closes Startable beans) and every later class failed with "connection refused"; fixed by a subclass whose `stop()` is a no-op (Ryuk removes it at JVM end) plus `destroyMethod=""`. (2) 17 cached contexts each hold a 10-connection Hikari pool, over Postgres' default 100 connections (first run: `SQLTransientConnectionException`, hang); fixed with `-c max_connections=500`.
- Containers per run: 20 before, 2 now (shared one + the one `DatabaseOutageHealthTests` needs because it stops its database; it uses `SharedPostgres.newContainer()` with the same wait and connection check). The run-2 failure (read timeout while Liquibase ran) cannot recur: the first context only starts after `select 1` succeeded.
- State isolation: `EventPublicationRegistryTests` used a fresh DB per class; now it shares the DB and finds its rows by a random id, and the failing-probe row stays in `event_publication` (no test failed on it in 4 runs). `FailingMigrationTests` runs its broken changelog on the shared DB: it fails on its own changeset, nothing is committed. All other classes already used unique subs/names.

## Contexts
- Before: 21 Spring contexts per run (17 cached, 4 hand-built). After: 19 (15 cached, 4 hand-built); `missCount` 15 from the cache DEBUG log (env var `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_TEST_CONTEXT_CACHE=DEBUG`; the `-D` form on the Gradle command line does not reach the test JVM).
- Moved to `application-test.properties` (identical for all): `jobhunter.cv-export.per-hour=6`, `jobhunter.files.max-other-bytes=1048576`, `jobhunter.files.ticket-seconds=2`. CvExportApiTests joined the plain ApiTest context; FilesLoggingTests joined FilesApiTests' context (same initializer, same properties).
- Not merged, because they differ by more than a property (extra `@Import` configs or fake servers): CvExtractApiTests, ProjectAnalysisApiTests (limit 2, ScriptedConfig), LlmLoggingTests, ApiDocsOffTests, FilesApiDiskTests, the event-probe classes, Crawl*/Startup*/Repositories (dynamic fake-server URLs), VirtualThreadTests. Merging those needs the shared `@TestConfiguration` and static fake servers of slow.report.md items 2 and 4, which change test bodies (next step).
- No `@DirtiesContext`, no assertion changed, no test deleted.

## Timings: where the integration time still goes
- Fast tier 23 s wall includes about 10 s Gradle/compile; slow ones there: KoogBoundaryTests (2-3 s), GitHub*ProviderTests, PoliteHttpTests (real HTTP delays).
- Integration still slow by cause (from slow.report.md, not yet fixed): CvExportApiTests busy test waits a real 5 s; CrawlSchedulerWiringTests 2 x `sleep(2500)` plus cron tick; FilesApiTests `sleep(2100)` ticket expiry; DatabaseOutageHealthTests 7 s (own container, 1.5 s Hikari timeout); each remaining cached context costs 1.5-2 s of start.
- Container start itself is now about 1 s per run instead of 20 s.

## Surprises
- Gradle `-D` flags do not reach the test JVM; use the env var (or an init script) for logging levels.
- Tag on the meta-annotation is inherited by `@ApiTest` because it is meta-annotated with `@IntegrationTest`.
- Leftover pgvector/ryuk containers from my two killed first attempts were removed by Ryuk later; nothing for you to do.
