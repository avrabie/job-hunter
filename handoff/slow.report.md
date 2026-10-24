# Slow test report (task 1.2), backend, 573 tests in 89 classes

Source: `build/test-results/test/*.xml` of five full runs; times below are from run 5 (a quiet run, 79 s of test time; wall clock of the five
runs 91 to 157 s, run 1 116 s). Flags: IT = `@IntegrationTest`, SBT = own `@SpringBootTest`, CONT = starts its own container,
SLEEP = sleep/delay/await in the class, PROP = own `@TestPropertySource`. Full per-class table: regenerate with the XML (scratchpad script).

## What one run costs
- 21 Spring contexts started per run (21 "Started ... in" lines). 17 come from the Spring test cache (cache stats after the run:
  `missCount` 17 without the failed context), 4 are hand-built in `SpringApplicationBuilder`: `DatabaseOutageHealthTests`,
  `EventPublicationRegistryTests`, `FailingMigrationTests`, `IssuerSettingTests`.
- 20 pgvector containers started per run (about 1.0 s each, log "Container pgvector/pgvector:pg16 started in PT1.0s"). Cause:
  `TestcontainersConfiguration` declares the `@ServiceConnection` container as a `@Bean`, so every distinct context gets its own
  container. Only two classes start a container by hand (`DatabaseOutageHealthTests`, `EventPublicationRegistryTests`).
  A single static container (companion `by lazy`, or `@Container` static) would remove about 17 x 1 s of container start and
  all the container-in-context shutdown noise. Start of each context itself is 1.5 to 2 s (Liquibase, Spring Modulith, beans).
- Contexts stay cached (limit 32) until the JVM ends, so connections from the 17 pools stay open for the whole run.

## 15 slowest classes (run 5)
| s | tests | class | flags | cause | fix, expected saving |
|---|---|---|---|---|---|
| 10.6 | 6 | ApiDocsContentTests | ApiTest | first class of the run: Ryuk + first container + the shared ApiTest context (about 6 s) plus OpenAPI generation per test (document rebuilt each call) | cache the parsed doc in a `lazy` per class; saving about 1 s. The context cost moves, it does not vanish |
| 9.8 | 2 | CrawlSchedulerWiringTests | IT, SLEEP, dyn props | `Thread.sleep(2500)` twice (5 s) + waits for a cron `*/3 * * * * *` tick (up to 3 s) + own context (DynamicPropertySource) | make the "nothing crawls" check wait 1 tick (tick PT1S, so 1.2 s) and use cron every second if the table allows; saving about 4 s |
| 7.9 | 14 | CvExportApiTests | PROP | the 503 busy test waits the real 5 s (`DefaultExportService` `wait = Duration.ofSeconds(5)`) + own context (`per-hour=6`) | make the wait a property (100 ms in tests): saving 4.9 s. Also see context sharing below |
| 7.2 | 1 | DatabaseOutageHealthTests | CONT | own container + own context + `postgres.stop()` + Hikari `connection-timeout` 1500 ms and a failing health check (about 1.5 s timeout) | lower timeout to 500 ms: saving about 1 s; reuse not possible (it stops the DB) |
| 4.5 | 28 | FilesApiTests | PROP, SLEEP | `Thread.sleep(2_100)` for the ticket expiry (`ticket-seconds=2`), two sleep(5), own context | set `ticket-seconds=1` and sleep 1.1 s: saving 1 s |
| 2.5 | 16 | PostingIngestTests | IT, SLEEP | awaitility on async listeners, 16 parallel ingests | none worth it |
| 2.2 | 2 | EventPublicationRegistryTests | CONT, SLEEP | own container, two app starts | accept, or use the shared container: about 1 s |
| 2.2 | 1 | StartupCrawlEnabledTests | IT | own context (run-on-startup) + sleep 500 ms | context cannot be shared (startup property); sleep to 200 ms: 0.3 s |
| 2.1 | 17 | CvExtractApiTests | PROP | own context (3 properties + scripted reader config) | see sharing |
| 2.0 | 9 | ProjectAnalysisApiTests | PROP | own context (`per-hour=2` + ScriptedConfig) | see sharing |
| 1.9 | 8 | PoliteHttpTests | SLEEP | 1.5 s server delay against a 300 ms read timeout, 400 ms pause test | delay 600 ms: saving 0.9 s |
| 1.8 | 1 | ConcurrentProvisioningTests | SLEEP | own context (`@Import(RegisteredEventsConfig)`) | see sharing |
| 1.8 | 7 | RepositoriesApiTests | dyn props | own context (DynamicPropertySource) | see sharing |
| 1.8 | 2 | ApiDocsOffTests | PROP | own context (`api-docs.enabled=false`) | cannot share; keep |
| 1.7 | 2 | VirtualThreadTests | SBT | own `RANDOM_PORT` context | keep (needs a real port) |
| 1.7 | 1 | BackendApplicationTests | IT | an `@IntegrationTest` with its own plain context | likely the shared plain one |
Also seen: `FilesLoggingTests` (3.5 s, run 2), `KoogBoundaryTests` 2 to 3 s, `FilesApiDiskTests` 2.5 s, `FailingMigrationTests` 2 s (hand-built contexts, their cost is the context start).

## Which classes force a new context (cache key differences)
- `@TestPropertySource`: ApiDocsOffTests, CvExportApiTests (`per-hour=6`), CvExtractApiTests (2 props), ProjectAnalysisApiTests
  (`per-hour=2`), FilesApiTests (2 props), FilesApiDiskTests (`min-free-bytes`), LlmLoggingTests (`llm.api-key`).
- `@DynamicPropertySource`: CrawlSchedulerWiringTests, StartupCrawlTests (2 classes), RepositoriesApiTests (each its own fake server URL).
- `@Import` of an extra `@TestConfiguration`: ProfileSavedEventTests, PostingIngestTests, UserRegisteredEventTests and
  ConcurrentProvisioningTests (event probes), ProjectAnalysisApiTests, CvExtractApiTests, LlmLoggingTests (fake model).
- Different initializers: `FilesVolumeInitializer` in FilesApiTests, FilesLoggingTests, CvExtractApiTests (FilesLoggingTests
  is a context of its own only because it lacks the two FilesApiTests properties).
- Different `webEnvironment`: VirtualThreadTests (RANDOM_PORT, own `@SpringBootTest`).
- Own container/own `SpringApplicationBuilder`: the four hand-built ones above. No `@DirtiesContext`, no `@MockitoBean`/`@MockBean` anywhere.

## Context-sharing opportunities (each removes about 2 s of start plus about 1 s of container)
1. Move the scalar rate-limit and size properties (`cv-export.per-hour`, `analysis.per-hour`, `cv.extractions-per-hour`,
   `files.*`) out of `@TestPropertySource` into `application-test.properties` with one value, and keep the tests per-user
   (they already use a fresh `sub`): merges CvExportApiTests, ProjectAnalysisApiTests, CvExtractApiTests, FilesApiTests,
   FilesLoggingTests into the plain `ApiTest` context, if the limit values are compatible (6, 2, 2 differ: use the lowest
   with a fresh user per test or change the limiter tests to read the limit from the bean). Expected: 4 to 5 contexts fewer, 8 to 12 s.
2. Register all test beans (event probes, `ScriptedConfig`, fake model) in one shared `@TestConfiguration` imported by
   `IntegrationTest`: merges 6 to 8 contexts into one. Expected 8 to 12 s.
3. One static Postgres container for the JVM (see above): about 17 s of container start and shutdown.
4. Fake HTTP servers (Greenhouse, GitHub) started statically in one object and the base URL passed through a property known up front
   (fixed from a holder read by `DynamicPropertySource`): merges Crawl*/Startup*/Repositories contexts. Expected 3 to 6 s.
Overall realistic target: from 21 to about 8 contexts and from 80 to about 40 s of test time.
