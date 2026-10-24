# Report: github-open-source-projects, tasks 2.1 to 2.3

Worktree: `.claude/worktrees/agent-ab69450fa3ecfcc83`. Nothing committed. Backend only.

## Tasks

- **2.2 done, ticked.** `GitHubRepositoryProviderTests`: 12 tests, 0 failures. Covers every scenario in the verify text.
- **2.3 done, ticked.** `RepositoriesApiTests`: 7 tests, 0 failures. `ModularityTests`: 2 tests, 0 failures (includes `modules.verify()`).
  The `./gradlew test --tests '*Repositor*'` filter was not run literally. The three new classes all match it. Full `./gradlew test` was not run.
- **2.1 implemented, NOT ticked.** `RepositoryCatalogTests`: 7 tests, 0 failures. One scenario cannot be done as written:
  "a second fake provider selected by value". With `enum class CodeProvider { GITHUB }` there is no second value to give a fake.
  The test covers selection by value for GITHUB and refuses two providers with the same value at start-up.
  Decision needed: add a second enum value in a test, or accept this scenario as untestable until a second provider exists.

## Verify output

- `./gradlew compileKotlin --offline`: BUILD SUCCESSFUL.
- `./gradlew test --offline --tests '*RepositoryCatalogTests' --tests '*GitHubRepositoryProviderTests' --tests '*RepositoriesApiTests' --tests '*ModularityTests'`: exit 0.
  Test counts from the XML: 7 + 12 + 7 + 2 = 28, 0 failures, 0 errors, 0 skipped.

## Files created

- `backend/src/main/kotlin/net/s4v3/backend/profile/RepositoryCatalog.kt` (public interface, sealed `RepositoryListing`, `Repository`)
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/RepositoryProvider.kt` (internal provider contract)
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/DefaultRepositoryCatalog.kt` (username check, provider by value, 5-minute cache)
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/GitHubRepositoryProvider.kt`
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/RepositoriesSettings.kt` (`jobhunter.repositories.*`)
- `backend/src/main/kotlin/net/s4v3/backend/web/internal/RepositoriesController.kt`
- `backend/src/test/kotlin/net/s4v3/backend/profile/internal/RepositoryCatalogTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/profile/internal/GitHubRepositoryProviderTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/RepositoriesApiTests.kt`
- `handoff/gh-repos.report.md` (this file)

## Files changed

- `backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt`: added only `enum class CodeProvider { GITHUB }` (group 1 owns this; overlap at merge).
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/UsernameRules.kt` (new file): minimal `isValidUsername(provider, username)` (group 1 owns this; overlap at merge).
- `openspec/changes/github-open-source-projects/tasks.md` (worktree copy): ticked 2.2 and 2.3.

Not touched: `ProfileValidator`, `llm/`, `cv/`, `ui/`, `profile/Profile.kt` beyond the enum line.

## Decisions and surprises for the lead

1. **Merge overlap with group 1.** `CodeProvider` and `isValidUsername` are minimal stand-ins. Keep one copy at merge. The `isValidUsername` regex is `[A-Za-z0-9](?:-?[A-Za-z0-9]){0,38}`, which is the GitHub rule from the brief.
2. **Missing change directory at start.** `openspec/changes/github-open-source-projects/` was absent from this worktree. I read the files from the main checkout (read only). The coordinator copied them in during the task, and the worktree copy matched.
3. **Items without required fields are dropped, not fatal.** A repository with no `full_name`, `name`, or `https` `html_url` is skipped, and the rest of the list is returned. The design does not say which of these to do. A body that is not a JSON array is `provider_unavailable`.
4. **`RepositoryProvider` has no `usernameRule` member.** The design lists it, but the catalog uses `isValidUsername` directly, so one rule remains.
5. **Cache.** A `ConcurrentHashMap` with expiry, no Caffeine (not a dependency). It prunes expired entries when it reaches 1000 entries. Only `Found` is cached.
6. **Logging.** The provider logs one line per call: the outcome class name and milliseconds. No username, URL or body.
7. **Controller.** No `CurrentUser` injection, since the endpoint does not need the caller's id. Authentication comes from `SecurityConfig` (`anyRequest().authenticated()`), and the 401 test passes through it. It reuses `ErrorBody` from `ProfileController.kt`.
8. **Test context.** `RepositoriesApiTests` sets the GitHub base URL with `@DynamicPropertySource`, so it gets its own Spring context. It ran fine on the shared Testcontainers Postgres.
9. **Not checked.** `ProfileApiTests` and `full-profile.json` were not run, since group 1 changes them. The `ProfileController` is unchanged.
