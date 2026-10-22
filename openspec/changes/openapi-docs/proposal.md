# Proposal

## Why

The backend now has two real API groups (the user's profile and the admin crawl schedule) and more will follow (postings, matching, drafts). Nothing lists them: the only description is the code and the READMEs. The planned React client is "OpenAPI-generated" (PLAN.md), which needs a machine-readable description anyway. A generated OpenAPI document plus a browsable page gives the owner, and later the UI build, one place that always matches the running code.

## What Changes

- Generate an OpenAPI 3 description of the backend's REST API from the code, served as JSON at `GET /api/openapi.json`.
- Serve a browsable page (Swagger UI) at `GET /api/docs`, with a bearer-token "Authorize" button so signed-in calls can be tried from the page.
- Both are reachable without a token (they describe the API; they hold no user data). Every other endpoint stays protected as it is today. A setting turns the whole feature off.
- Describe every existing endpoint: `GET`/`PUT /api/profile` and `GET`/`POST /api/admin/crawl-schedule` with their request and response shapes, their error bodies (400 validation, 401, 403, 409, 413) and the required role. The health endpoints are not part of it.
- Add a build-time guard: a test fails when a controller endpoint under `/api` has no entry or no summary in the generated document, so the page cannot silently fall behind the code.
- Keep the page and the JSON under `/api`, so the gateway and edge need no change.

### Observed about the current code

- The backend is Spring Boot 4.1.1 on servlet MVC. Controllers: `ProfileController` (`web.internal`) and `CrawlScheduleController` (`sources.internal`), plus test-only endpoints in test sources. Nothing generates API documentation today (no springdoc or Swagger dependency).
- The security chain permits only `GET /actuator/health/**` without a token and requires `ADMIN` under `/api/admin/**`.
- The gateway forwards `/api/**` to the backend without authorization checks and has the content security policy header switched off, so a Swagger UI served by the backend loads through it.
- springdoc-openapi 3.x is the line that targets Spring Boot 4 (3.1.1 is the newest release on Maven Central today). Compatibility with Boot 4.1.1 is not yet verified here, so the first task proves it.

### Out of scope

- Generating the TypeScript client for the UI, and wiring it into the UI build.
- Documenting the auth service or the gateway (the auth service's endpoints are standard OAuth/OIDC; its own discovery document stays the reference).
- Versioning of the API, API changes of any kind, or request examples beyond synthetic ones.
- Requiring a login to read the documentation.

## Capabilities

### New Capabilities
- `api-docs`: the generated OpenAPI document, the browsable page, their access rules and the guard that keeps them complete.

### Modified Capabilities

None. Security rules of existing endpoints do not change.

## Impact

- **Modules:** `accounts` (the security chain permits the documentation paths), `web` and `sources` (annotations on their controllers). No module gains a dependency on another.
- **Dependencies:** `org.springdoc:springdoc-openapi-starter-webmvc-ui` (the existing stack has no OpenAPI generator; the alternative is to maintain a YAML file by hand, which drifts). Adds Swagger UI static assets to the image.
- **Database:** none. **Network/deploy:** a new backend image; nothing in the cluster config changes.
- **Personal data:** none in the documents; examples are synthetic. **Per-user scoping:** unchanged; the documentation is public but every data endpoint still scopes by the token's `sub`. **Claude cost:** none.
