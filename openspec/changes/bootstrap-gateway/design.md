# Design

## Context

`gateway/` is a Kotlin scaffold: Boot 4.1.1, Spring Cloud 2025.1.3, `spring-cloud-starter-gateway-server-webflux`, actuator, Kotlin reactor and coroutines libraries, an empty `application.yaml`, no Spring Security. It is a standalone Gradle build. The reference is the gateway in Granite-Security/sample-shop: a pass-through router with `permitAll`, a nine-service `RouteLocator`, and an `application.yaml` whose comments record three production findings: stale pooled connections after deploys, `forward-headers-strategy` stripping `X-Forwarded-Host`, and Spring Cloud Gateway 5.x ignoring the old `spring.cloud.gateway.*` prefix for settings now under `server.webflux`. Decisions already made: reactive variant, no Spring Security, no authorization at the gateway, the edge sends `/api` and `/auth` here and `/` to the UI (see PLAN.md section 2, `bootstrap-k8s`). See proposal.md for scope.

## Goals / Non-Goals

**Goals:**
- Two routes and nothing else, expressed so that adding a service later is one more route.
- Settings that provably bind in this Spring Cloud version.
- Tests that run offline.

**Non-Goals:**
- Anything the proposal lists as out of scope; a custom filter chain; service discovery (addresses come from the environment).

## Decisions

**Routes as configuration.** The two routes are declared in `application.yaml` under the gateway's route properties rather than a Kotlin `RouteLocator`, since they are plain path predicates with no filters: `id: backend`, `uri: ${microservices.backend.uri}`, `Path=/api/**`; `id: auth`, `uri: ${microservices.auth.uri}`, `Path=/auth/**`. `Path=/api/**` matches `/api` and `/api/...` but not `/apix`. Alternative: a Kotlin `RouteLocator` bean as in sample-shop. Kept as an option if a route needs a filter. Environment binding: `microservices.backend.uri` and `microservices.auth.uri` map to `MICROSERVICES_BACKEND_URI` and `MICROSERVICES_AUTH_URI` through Spring's relaxed binding, with defaults `http://localhost:8081` and `http://localhost:9090`.

**No rewrite.** `auth` has the context path `/auth` and `backend` serves `/api/...`, so no `StripPrefix` or `RewritePath` filters. Fewer moving parts, and the URLs the browser sees equal the URLs the services see.

**Unknown paths.** With no matching route the gateway returns 404 on its own. No catch-all route. The edge sends only `/api`, `/auth` here, so this is a second line of defence for a misconfigured HTTPRoute.

**Settings prefix.** Settings that older versions kept under `spring.cloud.gateway.*` live under `spring.cloud.gateway.server.webflux.*` in this version, as sample-shop's comments record. The exact keys (HTTP client connect timeout, response timeout, pool `max-idle-time`, `max-life-time`, `eviction-interval`, default filters, x-forwarded behaviour) are taken from the configuration metadata of the pinned release, and a test asserts the effective values by reading the bound `HttpClientProperties` (or equivalent) so a misplaced key fails the build. This is the `GatewayConfigBindingTest` idea from sample-shop.

**Pool and timeouts.** Idle time 15s, max life 60s, eviction interval 15s (the values sample-shop measured after four deploys); connect timeout 5s; response timeout 60s. The response timeout applies to every route; long-running API calls (for example Claude drafting) are expected to be asynchronous in `backend`, and a route-level override is available when that changes. Alternative: no response timeout. Rejected: a hung downstream would hold connections indefinitely.

**Forwarded headers.** The gateway does not set `server.forward-headers-strategy` (sample-shop found that it strips `X-Forwarded-Host` before the proxy filters forward it). The gateway's built-in `X-Forwarded` filter is left on but must deliver the edge's host and scheme as first or only values; the default behaviour appends the gateway's own view to any incoming value, which for `Proto` would produce `https,http`. Spring and Tomcat/Netty take the first value, and a test with a stub downstream asserts what arrives. If the first value is not the edge's, the append properties for host and proto are switched off. `auth` has a fixed issuer, so a header problem would only affect redirects and the login page, not token validation.

**Secure headers without Spring Security.** The built-in `SecureHeaders` filter, named explicitly under `default-filters` rather than assumed to be on by default, adds `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Strict-Transport-Security` and others. The `Content-Security-Policy` header is disabled (via the filter's disable list) because the default policy would block inline styles and scripts on the login page that `auth` generates; the UI sets its own policy where it serves its pages. Alternative: add `spring-boot-starter-security` for header writers. Rejected: it also locks every route and actuator endpoint behind a login unless a `permitAll` chain undoes it (which is what sample-shop's `GateSec` is for). A test checks the headers on a proxied response and the absence of a CSP.

**Health.** Actuator exposes only `health` with the liveness and readiness probe groups enabled, and `management.endpoint.health.probes.enabled=true`. No health indicator depends on a downstream service. Gateway-specific actuator endpoints (`gateway`) stay unexposed because they would leak the route table. The probes are on the same port 8080; the edge never routes `/actuator` here (only `/api`, `/auth`), and `/api/actuator/...` is an ordinary API path forwarded to `backend`.

**Logging.** Defaults only; the Reactor Netty wire logger and Spring Cloud Gateway debug logging stay off, so no headers or bodies are logged. A log-capture test asserts a request with `Authorization`, a cookie and a query string leaves none of them in the log.

**Stub downstream in tests.** Tests start Reactor Netty `HttpServer` instances as stand-ins for `backend` and `auth` (random ports, recording the received request) and point the routes at them with `@DynamicPropertySource`. No new dependency; `WebTestClient` calls the gateway. Alternative: WireMock or MockWebServer. Rejected to avoid a dependency for something Reactor Netty already does.

**Ports.** Gateway 8080, `backend` 8081, `auth` 9090 for local runs (`backend` is started with `SERVER_PORT=8081`; its default 8080 would clash). In the cluster every pod has its own address, and `bootstrap-k8s` sets the two URIs to the Service names.

**New dependencies.** None. The Kotlin coroutines libraries in the scaffold are unused and may stay or go; they are harmless.

**Module boundaries and database schema.** No Modulith, no database, no Liquibase. Packages: `net.s4v3.gateway` (application and configuration properties).

## Risks / Trade-offs

- [Property keys differ from what the docs show for this version] → Take keys from the pinned release's metadata and assert effective values in a test.
- [Duplicate `X-Forwarded-*` values confuse `auth` redirects] → Test with a stub; switch off the append properties if the first value is wrong.
- [Disabling CSP at the gateway leaves API and auth pages without one] → API responses are JSON; the login page needs inline content; the UI sets its own. Revisit when the login page is customised.
- [Response timeout of 60s too short for future long calls] → Backend should use asynchronous jobs; per-route override exists.
- [`/auth/actuator/health` is reachable from the internet through `/auth/**`] → Reveals only status; can be blocked with a route predicate later.
- [Reactive stack beside the MVC services] → Contained in this one small service with no business logic.
- [A stale connection on the first request after a deploy still yields one 502] → Pool limits make it rare; retries are deliberately out of scope.

## Open Questions

- Whether a second hostname or a second UI will ever need host-based routing here (not needed now; the edge handles hostnames).
