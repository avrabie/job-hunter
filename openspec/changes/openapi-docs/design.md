# Design

## Context

The backend (Spring Boot 4.1.1, servlet MVC, Kotlin) serves `ProfileController` (`web.internal`, `/api/profile`) and `CrawlScheduleController` (`sources.internal`, `/api/admin/crawl-schedule`). `SecurityConfig` (`accounts.internal`) permits `GET /actuator/health/**`, requires `ADMIN` for `/api/admin/**` and an authenticated user for everything else. The gateway routes `/api/**` to the backend with no authorization of its own and has the content security policy header disabled. Tests use `@ApiTest` (MockMvc with a mock issuer).

## Goals / Non-Goals

**Goals:**
- A generated, always-current document and a browsable page, reachable through the existing gateway and edge.
- A guard that keeps new endpoints documented.

**Non-Goals:**
- A generated UI client, API versioning, documenting the auth service or the gateway.

## Decisions

### springdoc-openapi, not a hand-written file
`org.springdoc:springdoc-openapi-starter-webmvc-ui` scans the controllers and bundles Swagger UI, which gives the document and the page with one dependency. The existing stack has nothing for this, and a hand-written YAML drifts. The version is taken from Maven Central (3.1.1 at proposal time, the line that targets Spring Boot 4); the first task proves it starts and serves with Boot 4.1.1, and if it does not, the fallback is the `springdoc-openapi-starter-webmvc-api` artifact (JSON only) plus a pinned Swagger UI webjar served by hand.

- *Alternative:* `springfox`. Unmaintained for Boot 3 and later.
- *Alternative:* generate the document at build time into a file. It cannot see the running configuration, and the page would still need a runtime.

### Paths under `/api`
`springdoc.api-docs.path=/api/openapi.json` and `springdoc.swagger-ui.path=/api/docs`, because the gateway and edge forward only `/api/**` and `/auth/**`. Found while building: springdoc redirects `/api/docs` to `/api/swagger-ui/index.html` and serves the page's files from `/api/swagger-ui/**`, which is already under `/api`; with the defaults the page loads the Petstore sample, so `springdoc.swagger-ui.url=/api/openapi.json` is set explicitly. A test checks every asset URL of the page starts with `/api/` and that the page loads our document, since a wrong path would only show up in production.

### Open reads, nothing else
The security chain gets one rule before `anyRequest().authenticated()`: `GET` on `/api/openapi.json`, `/api/docs`, `/api/docs/**` and `/api/swagger-ui/**` is permitted. Other methods fall through to the existing rules (401). The `/api/admin/**` rule is unaffected because none of the documentation paths sit under it. The documents describe shapes only, so no authentication is needed to read them; the Authorize control lets a signed-in user try calls. Requiring a login to read the page would make the documentation useless to the UI build and to curl.

### Switch
`jobhunter.api-docs.enabled` (default `true`) feeds both `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled`, and the permit rule above is added only when it is on. When off the paths do not exist: an authenticated request gets 404, and an anonymous one gets 401 because `anyRequest().authenticated()` answers before routing. The spec states exactly that.

### Describing the endpoints
- Annotations from `io.swagger.v3.oas.annotations` (`@Tag`, `@Operation(summary)`, `@ApiResponse`) on the two controllers, with the error bodies as small named schemas (`ValidationError` with `error` and `fields`, and the schedule's `error`/`field`/`message`). The bearer scheme is declared once in an `OpenAPI` bean in `web.internal`; secured operations reference it by default and the public documentation paths are not part of the document.
- The role requirement is stated in the operation description (`Requires the ADMIN role`) and as the 403 response; OpenAPI has no standard field for roles.
- The `web` module owns the `OpenAPI` bean; `sources` annotates its own controller and does not depend on `web`. Annotations come from `swagger-annotations`, a transitive dependency, so no module imports another module's types.

### The guard
A test (`@ApiTest`) collects every `RequestMappingInfo` from `RequestMappingHandlerMapping` whose path starts with `/api/`, drops the documentation paths and anything under `/api/test/` or declared in test sources, fetches `/api/openapi.json`, and asserts every (method, path) pair appears with a non-empty `summary`. A second test registers a test-only controller method without a summary and shows the guard names it. The guard runs in the normal test task, so a new endpoint without documentation breaks the build.

### Module boundaries and database
`accounts` (security rule), `web` (OpenAPI bean, profile annotations), `sources` (schedule annotations). No new module dependencies and no schema change. `ApplicationModules.verify()` must still pass.

## Risks / Trade-offs

- [springdoc 3.x and Boot 4.1.1 are not proven together here] → task 1.1 starts the application with the dependency and fetches the document before anything else is built; the fallback is named above.
- [The page loads blank behind the gateway because an asset path is outside `/api`] → task the page test checks every asset URL and the deploy follow-up opens the page on s4v3.net.
- [Public documentation reveals the admin endpoint exists] → it reveals shape, not data or access; the endpoint stays ADMIN-only and its existence is not a secret worth a login wall.
- [Swagger UI's inline scripts under a strict content security policy] → the gateway currently has the policy disabled; if it is enabled later the page needs an exception, noted in the README.
- [The guard only checks that a summary exists, not that it is good] → reviews and the README convention cover quality.

## Migration Plan

Additive. Deploy the backend image; no cluster config change. Check on s4v3.net: `/api/openapi.json` returns the document, `/api/docs` renders it with both endpoint groups, an anonymous `GET /api/profile` from the page answers 401, Authorize with a token answers 200. Rollback: redeploy the previous image, or set `JOBHUNTER_API_DOCS_ENABLED=false`.

## Open Questions

- None blocking. Whether the UI build generates its client from this document is a separate change.
