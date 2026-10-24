# Simplification candidates

Read-only analysis (task 5.3 of `fast-build-and-code-map`). Nothing was changed. Facts come from grep and line counts on
this worktree; anything I could not confirm is marked **(unverified)**. Behaviour changes and major dependency upgrades
are out of scope. Paths are relative to the repository root. Effort S/M/L, risk low/medium/high.

Scale: backend main 16.8k lines incl. fonts; no dead Kotlin class was found (every `internal` class has a reference or is a
Spring bean by annotation). The UI has no unreferenced exports and no unused CSS class (checked by script, see C14). So the
gains are in duplication, stale text and a few oversized files, not in deleting large dead areas.

## Top 10 (value over risk)

| Rank | Id | Candidate | Effort | Risk | Value |
|---|---|---|---|---|---|
| 1 | C1 | Three identical per-user hourly limiters become one class | S | low | one concept instead of three copies |
| 2 | C2 | Stale module count and module text (README, config.yaml, test name) | S | low | docs stop contradicting code |
| 3 | C3 | Controller error-body helpers repeated (`error(status, code)` x3, `fileError`, `cvError`, 5 body classes) | S | low | one error-shape helper |
| 4 | C4 | `owner/repo` validation copied into `ProjectAnalysisController` | S | low | one rule, one place |
| 5 | C5 | Log-capture boilerplate repeated in 14 backend test files | M | low | tests shorter, intent visible |
| 6 | C6 | Local `HttpServer` fakes written 7 times in backend tests | M | low | one fake-server helper |
| 7 | C7 | `LegacyCvMover` one-off migration still runs at every start | S | medium | removes 107 lines and a runner (owner must confirm all rows moved) |
| 8 | C8 | `DefaultRepositoryCatalog` / `DefaultRepositoryDigestSource` copy the same cache and provider-map code | M | low | one small cache class |
| 9 | C9 | `KoogSpikeTests` and empty `applications`/`notifications` modules are leftovers | S | low | fewer "what is this" files |
| 10 | C10 | `ProfileValidator.check` (97 lines) and `ProfilePage.test.tsx` (1503) are the two largest units | M | medium | easier to read and change |

## 1. Dead or unused code

### C7 LegacyCvMover (one-off migration)
- Files: `backend/src/main/kotlin/net/s4v3/backend/files/internal/LegacyCvMover.kt` (1-107), `backend/src/test/kotlin/net/s4v3/backend/LegacyCvMoverTests.kt` (136).
- Issue: moves rows of the table `cv_document` of "the earlier single-CV feature" into the file cabinet on every start (`ApplicationRunner`). `grep -rn LegacyCvMover backend/src/main` finds only the class itself (it is a `@Component`). Once production has run it, it is a no-op that still costs a start-up query.
- Change: confirm on prod that `cv_document` is empty, then delete the runner and its test (and later the table by a Liquibase change). Nothing outside the code (OpenAPI, specs, k8s) refers to it (grep of `k8s`, `openspec/specs`: not checked in the specs, **unverified**).
- Effort S, risk medium (data must be moved first), value: removes a start-up side effect and 243 lines.

### C9 Spike test and empty modules
- `backend/src/test/kotlin/net/s4v3/backend/llm/KoogSpikeTests.kt`: its own header says "First contact with Koog ... Nothing here is production code". `docs/koog-notes.md:12` points to it as the "runnable example". It overlaps `KoogCvReaderTests` / `KoogProjectAnalystTests` (both use `FakePromptExecutor`). Unique coverage: shows `executeStructured` with a fake model and the `@LLMDescription` schema. Propose: keep only if the doc still needs it, else delete and edit the doc line.
- `backend/src/main/java/net/s4v3/backend/applications/` and `.../notifications/` contain only `package-info.java` (and `internal/package-info.java`). They are asserted by `ModularityTests.kt:17` ("exactly the eleven planned modules"). Not dead in the Modulith sense, but they are placeholders for roadmap items (PLAN.md). Propose: keep or drop them as an owner decision; if dropped, edit that test.
- Effort S, risk low.

### C11 Test-only code in `ui/src`
- `ui/src/test/fakeXhr.ts` is a test helper but lives next to production code; `grep -rl fakeXhr ui/src` gives only `FileCabinet.test.tsx` and `filesApi.test.ts`. The other test helpers are in `ui/src/test/`. Proposal: move to `ui/src/test/`. Effort S, risk low, value: production folders hold only production code.

### C12 Settings defaults that disagree with `application.yaml`
- `LlmSettings.kt:19` default `model = "Sonnet_5"` while `application.yaml` sets `Haiku_4_5` (and README says default `Haiku_4_5`). The yaml wins at runtime, so the Kotlin default is only seen in tests. Proposal: align the default (or drop it) so one value exists. This is a naming/consistency fix, not a behaviour change in production. Effort S, risk low.
- Other settings fields (`repairs`, `timeout`, `pause`, `minInterval`, `maxListBytes`, ...) each have at least one use in main code (grep per field, counts 1 to 7). I did not find an unused property.

### C13 Unused dependencies
- Not proven unused, only unreferenced in source: `spring-modulith-observability-api` (backend `build.gradle.kts:34`) and the runtime-only `spring-modulith-observability-core` / `spring-modulith-actuator` (`:48-49`). `grep -rn "observability\|Observation\|micrometer" backend/src` gives nothing. They may be needed for Modulith event metrics at runtime **(unverified)**. Check with `./gradlew dependencies` and a start-up trial by the owner before removal.
- Test-only: `archunit-junit5` is used (`ModuleViolationTests`, `KoogBoundaryTests`, `TokenAccessBoundaryTests`). `kotlin-test-junit5` and `kotlin("test")` (lines 58 and 64) both declared: probably redundant (**unverified**, same library family). Effort S, risk low.

### C14 What was checked and found clean
- UI: script over all non-test `.ts/.tsx` exports in `ui/src` found no export with zero outside references; all CSS class names in `ui/src/index.css` are referenced in `.ts/.tsx`.
- Backend: `JwtCurrentUser` has no reference by name, but it is the only `CurrentUser` bean (used by 8 controllers). `IssuerSettingCheck` is registered through `META-INF/spring.factories`. `OpenApiConfig` is used by springdoc; the OpenAPI document is also described in `k8s/README.md`, `backend/README.md`, `README.md`, and `ApiDocs*Tests`, so it is not removable.

## 2. Duplicated patterns

### C1 Three hourly limiters
- Files: `backend/src/main/kotlin/net/s4v3/backend/web/internal/AnalysisLimiter.kt` (25), `cv/internal/ExtractionLimiter.kt` (25), `cv/internal/ExportLimiter.kt` (25).
- Issue: the 25 lines are identical except the class name and the setting read (`settings.perHour`, `extractionsPerHour`, `perHour`). Same `HashMap<String, ArrayDeque<Instant>>`, same window code. Proof: `diff` of two files shows only these lines differ. No unit test exists for a limiter directly; each is covered by an API test: `CvExportApiTests.kt:228`, `ProjectAnalysisApiTests.kt:186`, `CvExtractApiTests.kt:316`. In memory only, one backend instance.
- Change: one `HourlyLimiter(limit: () -> Int)` class in a shared place (the module rules allow it in `web` or a small `shared`/`accounts` API; **check Modulith**: `cv` and `web` are different modules, so a class both can import must be public in one module), plus three tiny beans or one class constructed per setting. One unit test for the window.
- Effort S, risk low (the API tests stay as the safety net), value: the rule "per user per rolling hour" lives once.

### C3 Error bodies in controllers
- Files: private `error(status, code)` in `web/internal/RepositoriesController.kt:73`, `web/internal/ProjectAnalysisController.kt:110`, `cv/internal/CvExportController.kt:109` (same body: `ResponseEntity.status(status).body(mapOf("error" to code))`); `fileError` in `files/internal/FilesController.kt:43` and `cvError` in `cv/internal/CvExtractController.kt:25` (same, plus `message`); `PostingsController.kt:98` `invalid`; `ProfileController.kt:94-99` handlers.
- Body classes only for OpenAPI: `ErrorBody` (ProfileController.kt:85), `ValidationErrorBody` (:87), `ExportErrorBody` (CvExportController.kt:134), `ScheduleErrorBody` (CrawlScheduleController.kt:21). `ErrorBody` and `ExportErrorBody` are the same shape `(error: String)`.
- Change: one internal helper `errorResponse(status, code, vararg extras)` per module boundary (modules cannot share internals, so either a tiny public `web` API or keep one helper per module and drop the duplicates inside a module: `web` has two copies, `cv` has two). Merge `ExportErrorBody` into `ErrorBody` where modules allow.
- Effort S, risk low (JSON shape must stay byte-identical; API tests cover it), value: fewer near-identical lines in every controller.

### C4 Name validation repeated
- `profile/internal/DefaultRepositoryDigestSource.kt:10,58-62` (`isValidFullName`, `nameSegment`) and `web/internal/ProjectAnalysisController.kt:25,113-117` (`ownerRepoSegment`, `isOwnerRepo`), same regex `[A-Za-z0-9._-]{1,100}` and same dot-segment rule. The controller comment says "Same rule as the digest source". Cause: `isValidFullName` is `internal` to `profile`.
- Change: expose one check through the `RepositoryDigestSource` public API (`DigestResult.InvalidName` already exists: the controller can call `digests.digest` and map `InvalidName`, as it already does at line 96), then delete `isOwnerRepo`. The saved-project check at line 85 must stay before the limiter, so the order of answers needs a test first (400 vs 404): the existing test class `ProjectAnalysisApiTests` shows which cases exist.
- Effort S, risk low-medium (answer order), value: one rule.
- Also `profile/internal/UsernameRules.kt` (GitHub username) and the UI copy `ui/src/profile/rules.ts` mirror backend rules by design (browser check before sending). Not a candidate, noted so it is not flagged again.

### C8 Two catalog/source classes with the same cache
- `DefaultRepositoryCatalog.kt` (54) and `DefaultRepositoryDigestSource.kt` (62): same `byProvider` map with the same `require`, same `CacheKey`/`Cached`, same `store` with eviction, same `MAX_ENTRIES = 1000`; differences are TTL (5 vs 10 min) and the result type.
- Change: a small generic `ExpiringCache<K, V>(ttl, max)` used by both. Effort M, risk low (`RepositoryCatalogTests`, `RepositoryDigestSourceTests` cover it), value: caching logic readable once.

### C5 Log-capture boilerplate in tests
- **Done.** `backend/src/test/kotlin/net/s4v3/backend/LogCapture.kt` has `capture { ... }` (returns the events at TRACE, or the levels given; detaches and restores in a finally) and `captureAppLogs { ... }` (root INFO, `net.s4v3` at TRACE). No `ListAppender` is left in `backend/src`; the 15 logging test classes use the helper and keep their assertions. `./gradlew test` and `integrationTest` pass.
- `grep -rl ListAppender backend/src/test` lists 14 files (`*LoggingTests` x8 plus `ProjectAnalysisApiTests`, `CvExtractApiTests`, `CvExportApiTests`, `CrawlScheduleApiTests`, `CrawlSchedulerTests`, `LlmLoggingTests`). auth already has one helper, `auth/src/test/.../LogCapture.kt`; backend has none.
- Change: `LogCapture` helper in backend tests (`capture { ... }` returns the messages at TRACE). Effort M, risk low, value: each logging test shows only its marker and assertions.
- Coverage to keep: each `*LoggingTests` covers a different endpoint family (profile save, contact, posting create/update, posting search, matching, sources, files, tokens, LLM). They are not duplicates of each other; they could be merged into fewer classes (fewer Spring contexts) **(unverified which share a context)**.

### C6 Local HTTP fakes
- `HttpServer.create` in 7 test files: `MockIssuer.kt`, `FakeGreenhouse.kt`, `RepositoriesApiTests.kt:133` (`FakeGitHub`), `PdfRendererTests.kt:71`, `PoliteHttpTests.kt:19`, `GitHubRepositoryProviderTests.kt:18`, `GitHubDigestProviderTests.kt:20`. Each redoes start, port, handler map, close.
- Change: one `FakeHttpServer` (path to response, request log). Effort M, risk low, value: tests read as behaviour, not plumbing.

### C15 Other repeats (smaller)
- `DefaultExportService` `contentType` and `fileName` both `when (options.format)` over the same three cases: could be one enum property on `CvFormat`. S, low.
- `ProfileController` / `PostingsController` / `MatchesController` each compute `currentUser.id()`; fine, no action.

## 3. Layers that only forward
- `files/internal/FileCabinetService.kt` (17): one call to `JdbcFileRepository.master` plus a path lookup behind the public `FileCabinet` interface. It exists so `cv` can read the master CV without seeing `files.internal`; this is the module boundary, not waste. Keep.
- `accounts/internal/JwtCurrentUser.kt` (17) behind `CurrentUser`: same reason; keep.
- `profile/internal/RepositoryProvider.kt` and `RepositoryDigestProvider.kt` (15 each): two interfaces with one implementation each (`GitHub*Provider`), each wrapped by a `Default*` class. Only GitHub exists (`CodeProvider` has one value, `Profile.kt:75` "Only GITHUB for now"). The indirection serves a second provider that does not exist. Propose: leave, but record in the module README that it is deliberate; collapsing would be a design decision for the owner. Risk medium.
- Interface plus single `Default*` implementation pairs (`ExportService`/`DefaultExportService`, `RepositoryCatalog`/..., `ProfileService`/`JdbcProfileService`): required by Modulith (`internal` hides implementation). Not a candidate.

## 4. Longest classes and functions

Backend main (lines per file): `files/internal/FilesController.kt` 324 (upload, list, master, tickets, download, delete, OpenAPI text), `postings/internal/JdbcPostings.kt` 260 (insert, refresh, search SQL), `profile/internal/ProfileValidator.kt` 248 (field rules), `profile/internal/GitHubDigestProvider.kt` 187 (GitHub calls for a digest), `sources/internal/GreenhouseCrawl.kt` 176, `llm/internal/KoogCvReader.kt` 163, `cv/export/internal/CvBuilder.kt` 162, `profile/Profile.kt` 156 (data types), `llm/internal/KoogProjectAnalyst.kt` 134, `cv/internal/CvExportController.kt` 134 (about 60 lines are OpenAPI annotations).
Longest functions (my line-counting script, approximate): `ProfileValidator.check` 97 (ProfileValidator.kt:66), `Prefilter.hardRules` 54 (:40), `LegacyCvMover.moveOne` 46 (:59), `GreenhouseCrawl.crawlLocked` 43 (:91), `GoogleAccountService.map` 37 (auth, :42), `JdbcPostings.refresh` 36, `CvBuilder.section` 35, `FilesController.store` 33, `GitHubDigestProvider.fetch` 33, `JdbcProfileService.replace` 32.
auth main: `config/SecurityConfig.kt` 173 (filter chains, login, logout, Google), `config/AuthorizationServerConfig.kt` 114, `google/GoogleAccountService.kt` 91, `password/PasswordService.kt` 87. gateway main: all files under 50 lines.
UI (non-test): `profile/ProfileForm.tsx` 567 (the whole profile form: contact, location, experience, education, languages, publications, skills, preferences), `profile/types.ts` 364 (types plus blank factories plus `toRequestProfile`), `profile/ProfilePage.tsx` 356 (load/save/conflict/extract flow), `export/ExportPage.tsx` 341, `profile/OpenSourceSection.tsx` 338, `files/FileCabinet.tsx` 315, `files/filesApi.ts` 264, `index.css` 249, `profile/fields.tsx` 246, `postings/PostingsPage.tsx` 219.

### C10 Split the two largest units
- `ProfileValidator.check` (97 lines, one function) into one function per section (contact, location, experience, education, skills, preferences). Test file `ProfileValidatorTests.kt` (498) is the net. S/M, risk medium (error-field names are the API; tests assert them).
- `ProfileForm.tsx` 567: one component per section in `fields.tsx` style. M, risk medium **(unverified: tests in `ProfilePage.test.tsx` exercise it through the page, so they would catch regressions)**.
- `FilesController.kt` 324: about half is OpenAPI annotations and `FilesMessages`; moving messages and annotations out is cosmetic. S, low.

## 5. Misleading or inconsistent names

### C16 Module placement and names
- `web` module holds `ProfileController`, `PostingsController`, `RepositoriesController`, `ProjectAnalysisController`, `AnalysisLimiter`, `AnalysisSettings`, `OpenApiConfig` (all in `web/internal/`), while `matching`, `cv`, `files`, `sources` own their controllers. So "web" means "controllers of profile and postings", and `ProjectAnalysisController` sits in `web` but is about the profile's open source projects and uses `analysis` settings. A reader looking for the analysis rules finds them under `web`.
- Proposal: no move now (module moves change package names and `ModularityTests`); instead describe it in `docs/architecture.md` and the module README. If moved later: M, medium.
- `cv` module name covers two things (`cv/internal` extraction controller and `cv/export`); `ExtractionLimiter` and `ExportLimiter` live in `cv.internal`, `CvSettings` vs `ExportSettings` (`jobhunter.cv` vs `jobhunter.cv-export`). Acceptable, but `ExtractionLimiter` reads `CvSettings.extractionsPerHour` while the other two read `perHour`: cosmetic, disappears with C1.
- Test names: `PostingLoggingTests` vs `PostingsLoggingTests` (singular/plural, different endpoints); `TestEndpoints`/`TestNotesEndpoints` are test-only controllers and their names do not say what they prove (`TestNotesEndpoints` is the "A cannot read B" pattern, used by `ApiTest.kt:27`). S, low.
- `LlmFailureKind.FAILED` vs `NOT_CONFIGURED` is mapped to different codes by three controllers (`analysis_not_configured`, 503 text in `CvExtractController.kt:82`); consistent enough.

## 6. Tests that overlap

Counts of `@Test` are in the file names below; unique coverage given so the owner can judge.

| Tests | Overlap | Unique coverage |
|---|---|---|
| `ApiDocsTests` (64), `ApiDocsContentTests` (125), `ApiDocsOffTests` (28) | all three start a context to read `/api/openapi.json` or `/api/docs` | served to anyone and page files under `/api`; every endpoint has a summary (guard against a new undocumented endpoint, `:92`); off switch removes paths. Content tests assert wording of descriptions (profile error statuses, ADMIN requirement): implementation detail, candidate to thin out to the "every endpoint has a summary" guard. |
| `ModularityTests` (module set equals eleven names) | duplicates what `verify()` does not check: it pins a list. Breaks whenever a module is added. | the pin protects against an accidental module; low value. Rename or drop the list. |
| `ModuleViolationTests` | tests Modulith itself against a fixture (`net/s4v3/fixture`) | proves the build would fail for a violation. Tests the framework; could be removed since `ModularityTests.verify()` already runs on the real code **(owner judgement)**. |
| `KoogBoundaryTests` second test | tests the ArchUnit rule against `ai.koog.fake` fixture | proves the rule can fail; keep if you value a guard on the guard. |
| `*SchemaTests` (7 files, 27 to 76 lines) and `DatabaseBaselineTests` | each starts a context to check one migration's tables/constraints | constraint behaviour (unique, check, cascade). They assert table structure: implementation detail, but the only test of the SQL files. Could share one context (**unverified count of contexts**). |
| `*LoggingTests` (see C5) | same skeleton | each covers a different endpoint family; keep, share helper. |
| `GitHubDigestProviderTests` (284), `GitHubRepositoryProviderTests` (169), `RepositoriesApiTests` (160), `ProjectAnalysisApiTests` (311) | each rebuilds a GitHub fake | provider tests cover parsing and failure mapping; API tests cover HTTP statuses and limits. Overlap is in the "provider down gives 502" cases (**unverified, not compared line by line**). |
| `CurrentUserTests` (32), `PerUserIsolationTests`, `TokenAccessBoundaryTests` | all show that `sub` scopes data | `TokenAccessBoundaryTests` is the ArchUnit rule that no class reads the token except `JwtCurrentUser`; the others cover behaviour. Keep. |
| `ui/src/profile/profile.test.ts` `describe('toRequestProfile')` (:190) and `describe('contact block')` (:319-345) | contact trimming is tested at `:191` ("turns empty optional text into null, trims") and again at `:320` | second block adds the contact sample; likely mergeable **(unverified)**. |
| `ui/src/profile/ProfilePage.test.tsx` 1503 lines, 52 tests | page tests re-test save/conflict messages that `profile.test.ts` `saveProfile` (:273-318) already tests at API level | page tests cover rendering, focus and flows. Large size is the issue, not duplication. Split by flow (load/save, extraction, repositories, analysis). |
| `ui/src/build.test.ts` | runs a real Vite build in memory (slow, **unverified time**) | only guard that the CSP-free output has no inline script/style; keep, mark slow. |

Tests asserting wording or golden strings (implementation detail): `ApiDocsContentTests` (description text), `LabelsTests` (German/English label values, but the labels are the product), UI tests that match error strings from `api/messages.ts`. These break on edits of copy without a behaviour change. Proposed: assert status/code, not full sentences, where the text is not the feature. M, low.

## 7. Leftovers and stale text

### C2 Module count contradictions
- `openspec/config.yaml` says modules are `sources, postings, accounts, profile, matching, applications, llm, web, notifications` (nine, no `cv`, no `files`); `backend/README.md:61` says "Nine modules"; `ModularityTests.kt:17-23` expects eleven (adds `cv`, `files`). `backend/src/main/kotlin/net/s4v3/backend` has nine directories (`accounts cv files llm matching postings profile sources web`) plus two empty Java modules (`applications`, `notifications`) = eleven. So the README and `config.yaml` are stale. Proposal: update both lines (config.yaml is the change-process context). S, low.

### C17 Mail stand-in
- `auth/src/main/kotlin/net/s4v3/auth/mail/MailSender.kt:17-20`: `LoggingMailSender` is "Stand-in until SMTP exists" and logs the password reset link (with token) at INFO. `SecretsNotLoggedTests.kt:86` carves out an exception for it. Documented, but a known leftover and an open privacy risk in production logs **(behaviour, so out of scope to change; listed so the owner knows)**.

### C18 Stale text candidates
- Root `README.md` "Tests and builds" table (`./gradlew test`, `./gradlew build` "needs Docker") will contradict the fast/integration split of this change; task 8 already covers it.
- `README.md:3` and `ui/README.md:87` say "for now" (Greenhouse only, GitHub only): true today.
- `README.md` says CV extraction "answers 503" without a key: matches `CvExtractController.kt:82`. `gateway/README.md` and `ui/README.md` were not compared line by line (**unverified**).
- No `TODO`, `FIXME`, commented-out code or `@Deprecated` found by `grep -rnE "TODO|FIXME|@Deprecated" backend/src auth/src gateway/src ui/src`.

## 8. Dependencies
See C13. In `ui/package.json` every dependency has at least one import (`oidc-client-ts` 8 files, `react-router` 21 files; `vite` is used by `build.test.ts` and the build). `@types/node` is needed by `vite.config.ts`/`build.test.ts` **(unverified)**. Backend `lingua`, `pdfbox`, `openhtmltopdf`, `springdoc`, `koog` are all imported in main code (checked by grep).

## Summary
1. No large dead area: backend, auth, gateway and the UI have no unreferenced classes, exports, or CSS classes.
2. Best quick win: merge the three identical limiters (C1) and the error-body helpers (C3), and the copied `owner/repo` rule (C4).
3. Test code is where most repeats sit: log capture in 14 files (C5) and 7 local HTTP fakes (C6).
4. `LegacyCvMover` is a finished one-off migration that still runs at every start (C7); confirm prod data first.
5. Docs contradict code on the module count (C2): README, `config.yaml`, `ModularityTests` (nine vs eleven).
6. Two caches are copy-pasted (C8); two catalog interfaces serve a second provider that does not exist (section 3).
7. Biggest units: `ProfileValidator.check` (97 lines), `ProfileForm.tsx` (567), `ProfilePage.test.tsx` (1503).
8. The `web` module name misleads: it holds profile, postings and analysis controllers (C16).
9. Test judgement calls: `ModuleViolationTests`, `KoogSpikeTests`, wording asserts in `ApiDocsContentTests`.
10. File: `/Users/moldo/Documents/code/projects/job-hunter/.claude/worktrees/agent-a358309627423601d/docs/simplification-candidates.md`
