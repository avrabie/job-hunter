# web

Purpose: REST controllers that have no domain module of their own, plus the OpenAPI header. Other modules hold their own controllers (`cv`, `files`, `matching`, `sources`).

## Public API (this package)
- None. Only `internal/`.

## Internal (`internal/`)
- `ProfileController`: `GET`/`PUT /api/profile` (body cap `ProfileService.MAX_BODY_BYTES`, 256 KiB; its `@ApiResponse` text says 128 KiB: drift). `ProfileErrors` maps validation to 400 and version conflict to 409.
- `RepositoriesController`: `GET /api/profile/repositories`.
- `ProjectAnalysisController`: `POST /api/profile/open-source-projects/analyze`; only a project in the caller's saved profile; `AnalysisLimiter` (in-memory, per hour).
- `PostingsController`: `GET /api/postings`, `/api/postings/{id}` (shared data, any signed-in user).
- `AnalysisSettings`, `OpenApiConfig` (title, bearer scheme).
- Error bodies use fixed `error` codes and never echo input.

## Events
- None.

## Tables
- None.

## Settings
- `jobhunter.analysis.per-hour`; `springdoc.*` and `jobhunter.api-docs.enabled` in `application.yaml`.

## Calls
- `accounts`, `llm`, `postings`, `profile`.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `ProfileApiTests`, `RepositoriesApiTests`, `ProjectAnalysisApiTests`, `PostingsApiTests`, `ApiDocsTests`, `ApiDocsContentTests` (every endpoint documented), `ApiDocsOffTests`, `PerUserIsolationTests`.

Read on 2026-10-24 from: web/internal/*.kt, test file names.
