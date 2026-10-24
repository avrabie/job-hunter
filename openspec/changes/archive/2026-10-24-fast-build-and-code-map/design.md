# Design

## Context

Backend tests: 573 tests in 89 classes, about 81 s of test time (the largest classes: `ApiDocsContentTests` 11 s, `CrawlSchedulerWiringTests` 9 s, `CvExportApiTests` 8 s, `DatabaseOutageHealthTests` 7 s, `FilesApiTests` 5 s). Thirty classes carry the meta-annotation `@IntegrationTest` (`@SpringBootTest` + a shared pgvector Testcontainer + profile `test`); others start their own context or container. The other builds: auth 116 tests, gateway 30, UI about 364 (6 s). Compiler warnings today are few (about 10: `isPayloadTooLarge`, `isUnprocessableEntity`, five in `PdfRenderer` from openhtmltopdf); the IDE shows more. The run profile `dev` is only for `bootRun`; tests activate `test` themselves.

## Goals / Non-Goals

**Goals:** `./gradlew build` in under 20 s after the first compile, no flaky test over 5 consecutive full runs, `verify` under 90 s, zero unexplained deprecation warnings, one document a new reader can follow.
**Non-Goals:** see proposal.

## Decisions

1. **Tag, don't move.** Add `@Tag("integration")` to the `@IntegrationTest` annotation, and to the few other classes that start a Spring context or a container by hand. Tags are JUnit 5 and need no new dependency. Alternative: separate source set `integrationTest`: rejected, it needs moving files, duplicates dependencies and hides which tests belong together.
2. **Gradle tasks.** `test` uses `useJUnitPlatform { excludeTags("integration") }`; `integrationTest` (type `Test`, same classpath and test classes) uses `includeTags("integration")`, `shouldRunAfter(test)`; `verify` depends on both; `check`/`build` depend on `test` only. `integrationTest` runs with `maxParallelForks = 1` (the shared container) and the Docker requirement is documented. Alternative: make `build` run everything and use `-x`: rejected, the default must be the fast one.
3. **Flaky hunt.** Run `./gradlew clean verify` five times, plus `integrationTest` with `--rerun` under CPU load, collecting every failure (class, test, message, which run). For each: reproduce, find the cause, fix the cause. Typical causes to check first: real `Thread.sleep` and time windows (ten classes use sleep, delay or await), fixed ports, shared static state such as the in-memory limiters and caches, test order dependence on the shared database, Docker/Testcontainers start timing. A retry plugin is not used: it hides the problem.
4. **Speed.** (a) Injectable durations: the CV export busy test waits a real 5 s (the guard's wait becomes a constructor/property value, 100 ms in tests). (b) Share contexts: tests that differ only by one `@TestPropertySource` property are merged into a few context groups or use `@DynamicPropertySource`/config-property overrides on a shared context; count distinct contexts before and after (`-Dlogging.level.org.springframework.test.context.cache=DEBUG`). (c) One Testcontainers Postgres for the whole run (check `TestcontainersConfiguration` already does it, and that classes that start their own container are moved onto it). (d) Test classes over 3 s are each examined and the cause written in the task report (context start, container, sleep, PDF render, network fake).
5. **Deprecations, inventory first.** Sources: Kotlin compiler warnings (`./gradlew compileKotlin compileTestKotlin --rerun-tasks`), Gradle (`--warning-mode all`), IntelliJ inspections through the IDE MCP (`lint_files`/`get_file_problems` for deprecation), application startup logs (Spring Boot property and bean deprecation messages), dependency deprecations on Maven Central (the libraries we declare), and the UI toolchain (`npm`/`pnpm` and React warnings in tests). Each item gets: file, API, replacement, risk. Fix those with a direct replacement and a covering test; leave and justify the rest (for example `openhtmltopdf` `useFastMode` and `HttpStream` have no replacement in 1.1.100, so they stay with a `@Suppress` and a comment naming the library version).
6. **Understanding the code.** Read-only analysis agents write: `docs/architecture.md` (one diagram of ui, gateway, backend, auth, postgres; module map of the backend with each module's public API, events and tables; the main flows step by step with the classes involved: sign-in, save profile, extract CV, analyse repository, export CV, crawl and match; data model; security model; configuration index of every `jobhunter.*` property; where each kind of test lives), `docs/glossary.md` (profile, master CV, digest, analysis, source, posting, match, snapshot...), a `README.md` in each backend module package (purpose, public API, internal parts, events, tests; at most 40 lines), and `docs/simplification-candidates.md` (ranked by value over risk: dead or unused code, duplicated patterns such as the three limiters and the three controllers' error handling, layers that only forward calls, over-long classes, naming that misleads), each with files, proposed change, effort and risk. Facts only, every claim with a file path; unverified statements are labelled.
7. **Owner decides refactors.** After reading the candidates the owner picks; each picked item becomes its own small change or task with tests.
8. **Command table.** The root `README.md` gets a table: build and run fast tests, run the integration tier, full verify before deploy, run one test class, run the app (`bootRun` with profile `dev`), UI checks, image build and deploy check.

## Risks / Trade-offs

- [Fast tier hides regressions that only integration tests catch] → `verify` before every deploy, and the task report lists the spec areas that only have integration coverage.
- [Context sharing couples tests through state] → shared contexts only where tests already clean up after themselves; the 5-run flaky check catches coupling.
- [Docs rot] → short, generated from facts in the code where possible, linked from the root README; each module README states its date and the files it was read from.
- [Fixing deprecations changes behaviour] → one deprecation per commit-sized task, covered by the tests of that code, no dependency upgrades.

## Migration Plan

No deployment is needed for the test and docs work. Deprecation fixes in main code ship with the next normal release. Rollback is `git revert` of the task's commit.
