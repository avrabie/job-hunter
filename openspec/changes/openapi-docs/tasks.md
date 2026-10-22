# Tasks

## 1. Dependency and wiring

- [x] 1.1 Add `org.springdoc:springdoc-openapi-starter-webmvc-ui` (version from Maven Central, 3.1.1 at proposal time) to `backend/build.gradle.kts` and configure `springdoc.api-docs.path=/api/openapi.json`, `springdoc.swagger-ui.path=/api/docs` and `springdoc.swagger-ui.url=/api/openapi.json`, with the on/off switch `jobhunter.api-docs.enabled` (default true) feeding both springdoc enable flags. Verify an `@ApiTest` starts with the dependency and `GET /api/openapi.json` with a token returns 200 and an `openapi` field starting with `3.`. If the application does not start or the document is empty with Boot 4.1.1, stop and report before building more (fallback in the design).
- [x] 1.2 Verify the page: `GET /api/docs` (following its redirect) reaches the page, every script and stylesheet URL it references starts with `/api/` and answers 200, and the page's initializer points at `/api/openapi.json`.

## 2. Access rules

- [x] 2.1 Add the security rule permitting `GET` on `/api/openapi.json`, `/api/docs`, `/api/docs/**` and `/api/swagger-ui/**` without a token, placed before `anyRequest().authenticated()`, only when the switch is on. Verify with `@ApiTest` tests: anonymous GET of the document and the page answers 200; anonymous `GET /api/profile` and `GET /api/admin/crawl-schedule` answer 401; an anonymous POST to the document path answers 401 or 405 and changes nothing.
- [x] 2.2 Verify the switch with a test context where `jobhunter.api-docs.enabled=false`: an authenticated `GET /api/openapi.json` and `GET /api/docs` answer 404, an anonymous one answers 401, and `GET /api/profile` without a token still answers 401.

## 3. Describing the endpoints

- [x] 3.1 Add the `OpenAPI` bean in `web.internal` (title, description, the bearer security scheme) and annotate `ProfileController` with a tag, summaries and responses (200, 400 with the validation error schema, 401, 409, 413 as they exist in the code). Verify with a test that reads the document and asserts `GET`/`PUT /api/profile`, the profile schema, the listed error statuses on `PUT`, and the bearer scheme.
- [x] 3.2 Annotate `CrawlScheduleController` with a tag, summaries, responses (200, 400 with its error schema, 401, 403) and the sentence that the `ADMIN` role is required. Verify with a test that the document lists `GET`/`POST /api/admin/crawl-schedule` with those statuses and a bearer requirement.
- [x] 3.3 Verify the document holds no stored data and only synthetic examples: a test posts a marker value into a profile and a schedule, fetches the document, and asserts the marker never appears in it.

## 4. The guard

- [x] 4.1 Implement the guard test comparing every request mapping under `/api/` (excluding documentation paths and test-only endpoints) with the document, requiring a non-empty summary. Verify it passes for the current endpoints, and with a second test that adds a test-only controller method without a summary, that the guard fails and names the method and path.
- [x] 4.2 Verify `ModularityTests` passes (no new module dependency; `sources` and `web` use only the swagger annotations).

## 5. Docs and integration

- [x] 5.1 Document in `backend/README.md` where the page and the document are, how to try an authenticated call (Authorize with an access token), the switch, the rule that every new endpoint needs a summary, and the content security policy note; update the `web` row in `PLAN.md`. Verify the setting names match the code.
- [x] 5.2 Run the full backend build (`./gradlew build` in `backend/`) and verify it passes.

## Workflow follow-up

- Build and push the backend image, bump the tag in `k8s/overlays/prod/kustomization.yaml`, preview with `kubectl --context s4v3 diff -k k8s/overlays/prod`, apply.
- With the user's go-ahead, open `https://s4v3.net/api/docs` and check it renders both endpoint groups; check `https://s4v3.net/api/openapi.json`. Note the deploy in `k8s/README.md`.
- Archive the change (sync `api-docs`), then continue with `matching-prefilter`.
