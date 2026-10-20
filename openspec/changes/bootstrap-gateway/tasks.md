# Tasks

All work is in `gateway/`. Commands run from that folder (`cd gateway && ./gradlew ...`). No container runtime is needed; tests use local stub servers.

## 1. Routes and configuration

- [x] 1.1 Look up the configuration property keys for routes, HTTP client timeouts, pool settings, default filters and x-forwarded behaviour in the pinned Spring Cloud Gateway release's metadata, and record the chosen keys in `gateway/README.md`; verify each key appears in the release's configuration metadata
- [x] 1.2 Add the test support: Reactor Netty stub servers for `backend` and `auth` that record received requests, wired into the routes through `@DynamicPropertySource`; verify a trivial test sends a request through the gateway to a stub
- [x] 1.3 Declare the `backend` and `auth` routes in `application.yaml` with the URI properties and local defaults (`8081`, `9090`), no path rewriting; verify with tests that `/api/ping` and `/auth/.well-known/openid-configuration` reach the right stub with unchanged path, query, method and body
- [x] 1.4 Verify unknown paths: `/internal/secrets`, `/apix/foo` and `/authority` return 404 and no stub receives a request
- [x] 1.5 Verify override and defaults: with `MICROSERVICES_BACKEND_URI` set the route follows it, and with nothing set the targets are `localhost:8081` and `localhost:9090`

## 2. Pass-through and forwarded headers

- [x] 2.1 Verify the `Authorization` header, cookies and bodies arrive at the stub unchanged, a missing token is not rejected by the gateway (the stub's 401 reaches the client with its headers), and the gateway adds no identity header
- [x] 2.2 Verify responses relay status, body, `Set-Cookie` and `Location` unchanged (stub answers 302 with `Location`)
- [x] 2.3 Verify forwarded headers: a request with `X-Forwarded-Host: s4v3.net` and `X-Forwarded-Proto: https` reaches the `auth` stub with those as the first or only values, and a direct request gets the gateway's view; if the first values are wrong, switch off the append properties and re-run
- [x] 2.4 Verify downstream failures: a stopped stub gives 502 with no downstream address in the body, a stub that never answers gives 504 within the configured response timeout (use a short timeout in the test), and the gateway recovers when the stub is started again

## 3. Hardening

- [x] 3.1 Add `SecureHeaders` as an explicit default filter with the content security policy disabled; verify a proxied response carries `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` and `Strict-Transport-Security`, has no `Content-Security-Policy`, and has a single value when the stub already sets one of them
- [x] 3.2 Set connect timeout 5s, response timeout 60s and the pool values (idle 15s, life 60s, eviction 15s); verify with a binding test that the effective values of the HTTP client properties equal these and that a setting under an ignored prefix makes the test fail
- [x] 3.3 Verify with a log-capture test that a request carrying an `Authorization` header, a cookie and a query string leaves none of them in the log at the default level
- [x] 3.4 Verify the gateway sets no cookie of its own on proxied responses

## 4. Runtime

- [x] 4.1 Configure actuator to expose only `health` with liveness and readiness probes that do not depend on downstream services; verify both return 200 with both stubs down, `/actuator/env` and `/actuator/gateway/routes` return 404, and `/api/actuator/health` is forwarded to the `backend` stub
- [x] 4.2 Set the default port 8080 (overridable with `SERVER_PORT`); verify the application listens on 8080 and on a port given by the variable
- [x] 4.3 Write `gateway/README.md`: ports, routes, environment variables, how to run `gateway`, `backend` (with `SERVER_PORT=8081`) and `auth` together, and the setting keys from task 1.1; verify by following it with the three services running that `http://localhost:8080/auth/.well-known/openid-configuration` returns the discovery document and `http://localhost:8080/api/` returns 401 without a token (needs `backend` and `auth` implemented; otherwise verify against two stand-in servers and repeat when they exist)
- [x] 4.4 Run `./gradlew build` offline from clean (no `backend` or `auth` running) and verify all tests pass

## 5. Integration with the other services (needs `backend` and `auth` implemented)

- [x] 5.1 Run all three locally, open the generated login page of `auth` through `http://localhost:8080/auth/...` in a browser, and verify it renders with its styles and that signing in redirects back using the gateway's address
- [x] 5.2 Obtain a token through the gateway (authorization code with PKCE against `/auth`), call `/api/` through the gateway with it, and verify `backend` accepts it and creates the user record keyed by the UUID `sub`
- [x] 5.3 Stop and restart `backend` while the gateway runs and verify the first requests return 502 or succeed and later requests succeed without restarting the gateway

## Workflow follow-up

- Archive the change after groups 1 to 5 pass.
- Follow-up changes: rate limiting and bot protection, request size limits, retries or circuit breakers if they prove necessary, metrics and tracing, a UI route if the UI is ever served through the gateway.
