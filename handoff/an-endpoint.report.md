# Report: an-endpoint (task 4.1)

## Tasks finished

- 4.1 `POST /api/profile/open-source-projects/analyze` in `web/internal/`. Ticked in `openspec/changes/analyze-open-source-projects/tasks.md`, because `./gradlew build` passes.

Verify output:
- `./gradlew test --tests '*ProjectAnalysisApiTests*'`: 9 tests, 0 failures.
- `./gradlew build` (backend, exit 0, BUILD SUCCESSFUL in 1m 15s): 503 tests in 82 result files, 0 failures, 0 errors, 0 skipped. `ModularityTests` (3 cases) and `ModuleViolationTests` (3 cases) pass.

## Tasks not finished

None.

## Files created or changed

Created:
- backend/src/main/kotlin/net/s4v3/backend/web/internal/ProjectAnalysisController.kt
- backend/src/main/kotlin/net/s4v3/backend/web/internal/AnalysisLimiter.kt
- backend/src/main/kotlin/net/s4v3/backend/web/internal/AnalysisSettings.kt (`AnalysisSettings` plus `AnalysisConfig`, key `jobhunter.analysis.per-hour`, default 10)
- backend/src/test/kotlin/net/s4v3/backend/ProjectAnalysisApiTests.kt

Changed:
- backend/src/main/resources/application.yaml (new `jobhunter.analysis.per-hour: 10` block, after `jobhunter.cv`)
- openspec/changes/analyze-open-source-projects/tasks.md (4.1 ticked only)

## Test coverage (ProjectAnalysisApiTests, fakes for the digest source and the analyst)

1. Saved project analysed: 200 with summary, architecture, techStack, files, truncated, analyzedAt; the digest reaches the analyst; profile version stays 1.
2. Project saved by another user only: 404 `project_not_found`; no digest, no analyst call.
3. Malformed input (`../x`, `a/b/c`, empty, `octocat/..`, provider `NOPE`): 400 `invalid_request`; no outside call.
4. Anonymous: 401.
5. Hourly limit (property set to 2 in the test): third call 429 `too_many_analyses` before any outside call; a second user still analyses.
6. Repository not found: 404 `repository_not_found`; model not called.
7. Provider unavailable / rate limited: 502 `provider_unavailable`, 429 `provider_rate_limited`; model not called.
8. Model not configured: 503 `analysis_not_configured`; unusable answer: 502 `analysis_failed`.
9. Logs: repository text, model answer and repository name never appear; the capture is proven alive with a marker line.

## Decisions and deviations

- Order in the controller: provider and name shape (400), then the saved-project check (404), then the limiter (429), then the digest, then the analyst. This follows design decision 5 and the spec's "no outside call" scenarios.
- The name rule is copied into the controller. `isValidFullName` lives in `profile.internal` and `web` must not reach into it. The rule is the same: two parts, 1 to 100 characters of `[A-Za-z0-9._-]`, no `.` or `..`.
- `AnalysisLimiter` is a copy of `ExtractionLimiter` with its own counter, as design decision 5 says.
- `ErrorBody` is reused from `ProfileController.kt` (same package). `AnalysisBody` and `AnalyzeRequest` are new.
- No Swagger `requestBody` annotation: it clashes with Spring's `@RequestBody` in this package, and springdoc infers the body from the parameter type.
- A body that is not JSON gets Spring's default 400 with no `error` field. The spec only asks for 400 `invalid_request` on bad field values, so I left it. Say if you want a handler for it.
- Logs: one success line, "Project analysis finished in N ms". No name, input or output is logged.

## Anything surprising

- None blocking. The worktree is on the branch `worktree-agent-a3d0c15f1929a358a`. Nothing was committed.
- `application-dev.yaml` has no `analysis` block; the default of 10 applies there too.
