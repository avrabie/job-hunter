# profile

Purpose: the master profile (one JSON document per user, optimistic version) and read access to public code-hosting data (repository list, repository digest).

## Public API (this package)
- `ProfileService`: `get`, `parse` (strict), `replace` (validates, checks version). Throws `ProfileInvalidException`, `ProfileConflictException`.
- `ProfileDirectory`: paged read of all profiles, for modules that act for every user (`matching`). Not on HTTP.
- `Profile.kt`: the model (`Profile`, `StoredProfile`, `ProfileUpdate`, `OpenSourceProject`, `ProjectAnalysis`, `Skill`, `Preferences`, `CodeProvider`, ...).
- `RepositoryCatalog` / `RepositoryListing`, `RepositoryDigestSource` / `DigestResult` / `RepositoryDigest`.
- `ProfileSaved(userId, version)`: event.

## Internal (`internal/`)
- `JdbcProfileService` (implements both service interfaces; the version check is inside the SQL), `ProfileValidator`, `ProfileJson`, `UsernameRules`.
- `DefaultRepositoryCatalog` (5 min cache), `DefaultRepositoryDigestSource` (10 min cache), `GitHubRepositoryProvider`, `GitHubDigestProvider`, `RepositoriesSettings`.

## Events
- Publishes `ProfileSaved` after a successful replace; consumed by `matching`. Consumes none.

## Tables
- `profile` (`db/changelog/002-profile.sql`).

## Settings
- `jobhunter.repositories.*` (GitHub URLs, timeouts, digest caps).

## Calls
- No other module. HTTP out to GitHub.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `ProfileApiTests`, `ProfileServiceTests`, `ProfileSchemaTests`, `ProfileSavedEventTests`, `*LoggingTests`, `RepositoriesApiTests`, `ProjectAnalysisApiTests`.
- `profile/internal/`: `ProfileValidatorTests`, `RepositoryCatalogTests`, `RepositoryDigestSourceTests`, `GitHub*ProviderTests`.

Read on 2026-10-24 from: profile/*.kt, profile/internal/JdbcProfileService.kt, DefaultRepository*.kt, RepositoriesSettings.kt, db/changelog/002-profile.sql, test file names.
