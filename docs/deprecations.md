# Deprecation inventory

## Status (2026-10-24, after the first fix batch)

Fixed: the four Spring test matchers (`isUnprocessableEntity`, `isPayloadTooLarge`), `HttpStatus.PAYLOAD_TOO_LARGE` in `FilesController.kt` (found by the IDE inspections, not by the compiler), and the two unnecessary `!!` in `auth`. Left on purpose, with a file-level `@Suppress("DEPRECATION")` and a comment naming openhtmltopdf 1.1.100: `useFastMode()`, `HttpStream`, `HttpStreamFactory` in `PdfRenderer.kt`. Compiler output of `backend` and `auth` after the batch: no warnings.
Still open (IDE hints, not deprecations): `Legacy Long overload can be converted to Duration` in `KoogCvReader.kt:92` and `KoogProjectAnalyst.kt:47`, about 15 unused `e` parameters in catch blocks, and the SQL-inspection noise ("no data sources configured") that disappears when a data source is set up in the IDE.


Collected 2026-10-24 on branch worktree-agent-a8e23dd2c75a86ad1 (task 2.1 of `fast-build-and-code-map`). Read-only: no code changed, no tests run except `vitest run` once. Stack: Kotlin 2.3.21, Spring Boot 4.1.1 (spring-test 7.0.x), Gradle 9.8.1, React 19.3.1, React Router 8.5.0, Vite 8.3.4, vitest 5.0.5, TypeScript 6.0.3.

## Summary

| Project | Kotlin compiler (main) | Kotlin compiler (test) | Gradle summary | Startup / yaml | Deps | UI toolchain |
|---|---|---|---|---|---|---|
| backend | 6 deprecation warnings (3 APIs, 1 file) | 4 (2 APIs, 2 files) | 0 | none found (not started) | 0 deprecated, 1 stale | n/a |
| auth | 2 warnings, both "unnecessary `!!`" (not deprecations) | 0 | 0 | none found (not started) | 0 | n/a |
| gateway | 0 | 0 | 0 | none found (not started) | 0 | n/a |
| ui | n/a | n/a | n/a | n/a | 0 | 0 (tsc, vite build, oxlint, vitest all clean) |

- Total real deprecations: 10 compiler warnings, grouped into 5 rows below (3 in main code, 2 in tests).
- Java compile warnings: none (no Java sources compiled with warnings; the Kotlin tasks were the only compile output).
- IDE inspections: not used. IntelliJ MCP is reachable but only the project `/Users/moldo/Documents/code/projects/job-hunter` (main checkout) is open, not this worktree, so results would describe another checkout. Compiler output used instead.

## Method and raw results

- (a) `./gradlew compileKotlin compileTestKotlin --rerun-tasks --warning-mode all --console=plain` in `backend`, `auth`, `gateway`: all BUILD SUCCESSFUL (12 s, 2 s, 14 s). No `e:` lines.
- (b) Gradle: no "Deprecated Gradle features were used" summary in any of the three logs (grep for "deprecat" matched only Kotlin lines). Gradle only suggested enabling the configuration cache (a hint, not a deprecation).
- (c) Startup: apps were not started (no database, per instructions). Static read of `application*.yaml`, see section below.
- (d) IDE: see summary.
- (e) Dependencies: Maven Central `maven-metadata.xml` and npm registry `latest` checked, see section below.
- (f) UI: `npx tsc -b` no output (exit 0 apart from the missing node_modules at first, which I satisfied by symlinking the main checkout's `ui/node_modules` temporarily, removed afterwards); `npx vite build` built 138 modules, no warnings; `npx oxlint --deny-warnings` no output; `npx vitest run` 31 files, 364 tests passed, no deprecation, `act(...)` or React warnings in the filtered output. `pnpm` is not installed on this machine, so `pnpm install` was not run.

## Table

| file:line | API or setting | Replacement (verified?) | Risk | Decision |
|---|---|---|---|---|
| `backend/src/main/kotlin/net/s4v3/backend/cv/export/internal/PdfRenderer.kt:23` | `PdfRendererBuilder.useFastMode()` (`@Deprecated` in openhtmltopdf-core 1.1.100, verified in class file) | None in 1.1.100. Fast mode is the only mode in the 1.1.x line; the call is effectively a no-op marker. Replacement not verified beyond "none found in the 1.1.100 class". | low | leave: keep with `@Suppress("DEPRECATION")` and a comment naming 1.1.100 (as design.md decision 5 says). Removing the call is possible but its effect was not verified. |
| `.../cv/export/internal/PdfRenderer.kt:4,5,38,39` (import `HttpStream`, import `HttpStreamFactory`, `RefusingHttp : HttpStreamFactory`, `getUrl(): HttpStream`, `object : HttpStream`) | `HttpStreamFactory` / `HttpStream` interfaces (deprecated in Java) used with `useHttpStreamImplementation(RefusingHttp)` at line 26 | None found in 1.1.100: `useHttpStreamImplementation` itself is the only hook in `BaseRendererBuilder`. The code already blocks fetching via `useUriResolver { _, _ -> null }` (line 25), so the HTTP factory is a second guard only. Replacement not verified. | medium (it is the second guard of the no-network rule; a test for it exists in cv-export tests, not checked here) | leave: `@Suppress("DEPRECATION")` on the object and imports' use sites, comment naming 1.1.100. |
| `backend/src/test/kotlin/net/s4v3/backend/CvExtractApiTests.kt:188` | `status().isUnprocessableEntity` (deprecated in Spring Framework 7) | `status().isUnprocessableContent` (verified present in spring-test 7.0.9 via javap) | low (same status code 422, test-only) | fix now, covered by the test itself |
| `.../CvExtractApiTests.kt:198`, `.../FilesApiTests.kt:182,194` | `status().isPayloadTooLarge` (deprecated) | `status().isContentTooLarge` (verified present in spring-test 7.0.9 via javap; same 413) | low (test-only) | fix now, covered by the tests themselves |
| `auth/src/main/kotlin/net/s4v3/auth/google/GoogleAccountService.kt:34:102`, `auth/src/main/kotlin/net/s4v3/auth/token/AnyClientRefreshTokenGenerator.kt:20:43` | Not deprecations: compiler warning "Unnecessary non-null assertion (`!!`)" on `transaction.execute { ... }!!` and `context.registeredClient!!` | Drop the `!!` (the Java return types are now seen as non-null; likely Spring 7 JSpecify annotations: inferred, not verified) | low-medium: if the annotation is wrong at runtime, a null would become a different exception | fix now only if the owner wants zero compiler warnings; tests exist for both classes (not checked). Listed because they are the only other compiler warnings. |

## Application properties (c): static read, not started

Files read: `backend/src/main/resources/application.yaml`, `application-dev.yaml`, `auth/.../application.yaml`, `application-dev.yaml`, `gateway/.../application.yaml`. I could not confirm against actual startup logs, only against my knowledge of the Spring Boot 4 / Spring Cloud 2025.x migration notes, so every row is "unverified".

| Setting | Observation |
|---|---|
| gateway `spring.cloud.gateway.server.webflux.*` (routes, default-filters, httpclient, trusted-proxies, filter.secure-headers) | Already the new prefix (the old `spring.cloud.gateway.*` is the deprecated one). No finding. |
| gateway `trusted-proxies` placed under `...webflux:` | New location per the 5.x docs (unverified against the jar). No finding. |
| `spring.threads.virtual.enabled`, `spring.servlet.multipart.*`, `spring.datasource.hikari.*`, `management.*` | Current names in Boot 4 as far as I know. No finding. |
| `spring.modulith.events.jdbc.schema-initialization.enabled` | Current Spring Modulith property (version bundled with Boot 4.1.1). Unverified against jar. |
| `springdoc.*` | Current for springdoc 3.1.1. |
| `server.forward-headers-strategy: framework`, `server.servlet.context-path` | Current. |
| `logging.level.ai.koog: "OFF"` | Not a deprecation. |

To check for real: run each app once with the `dev` profile against a throwaway database and grep the log for `deprecated`; or add `spring-boot-properties-migrator` temporarily as `runtimeOnly` (no change was made).

## Dependencies (e)

| Dependency | Finding |
|---|---|
| `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.100` | Latest release (updated 2026-10-22). The `io.github.openhtmltopdf` group is already the maintained one (the old `com.openhtmltopdf` was relocated). Not deprecated. Source of the 6 backend main warnings. |
| `com.github.pemistahl:lingua:1.2.2` | Latest release on Maven Central, last updated 2022-08-02: stale (4 years), no deprecation or relocation marker found in the metadata. A newer project line may exist under another coordinate: unverified. leave. |
| `ai.koog:koog-agents:1.3.0`, `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`, `org.apache.pdfbox:pdfbox:3.0.8` | Latest releases per Maven Central metadata. No marker. |
| `com.tngtech.archunit:archunit-junit5:1.5.1` | Not checked. |
| Other backend, auth and gateway dependencies | Versions managed by the Spring Boot BOM; starter names match the Boot 4 modular starters (`spring-boot-starter-webmvc`, `...-security-oauth2-*`, `...-liquibase`, `...-validation`). No deprecated starter names found. |
| `io.spring.dependency-management:1.1.7` plugin | Not checked on the Gradle plugin portal; unverified whether still maintained. |
| npm: `oidc-client-ts@3.5.0`, `react-router@8.5.0`, `@vitejs/plugin-react@6.1.2`, `oxlint` (1.88.0 declared, 1.89.0 latest), `vitest@5.0.5`, `typescript` | `latest` metadata has no `deprecated` field for any of them. `typescript` latest is 7.0.2; we use `~6.0.3`, not a deprecation. Other npm packages not checked. |

## Decisions in short

- Fix now (all test-only, low risk): 4 matcher call sites (rows 3 and 4). Replacements verified in spring-test 7.0.9.
- Optional: 2 redundant `!!` in auth.
- Leave: `useFastMode` and `HttpStream`/`HttpStreamFactory` in `PdfRenderer.kt`; add `@Suppress("DEPRECATION")` and a comment naming openhtmltopdf 1.1.100.
- Not verifiable here: startup-time property deprecations, IDE inspections, lingua's successor.
