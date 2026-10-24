# gateway: code map

Stateless Spring Cloud Gateway (reactive) behind Traefik. Port 8080. Run instructions are in `README.md`; this file is
the structure map. Longer text: `docs/architecture-auth-gateway-ui.md`.

## What is here

Two Kotlin files in `src/main/kotlin/net/s4v3/gateway/`:
- `GatewayApplication.kt`: the entry point.
- `DownstreamErrorHandler.kt`: a refused or dropped downstream connection becomes `502 {"error":"bad_gateway"}`.

Everything else is `src/main/resources/application.yaml`:
- Routes: `/api/**` to `backend`, `/auth/**` to `auth`; paths are not rewritten; anything else is 404.
- Default filter `SecureHeaders` (CSP disabled so the generated login page of `auth` works).
- Timeouts: connect 5 s, response 60 s; pooled connections idle 15 s, max life 60 s.
- `trusted-proxies`: private and loopback ranges here, but `k8s/base/gateway.yaml` overrides it to `.*`.
- Health: `/actuator/health/liveness` and `/readiness` only.

## Not here

No token check, no roles, no sessions, no rate limiting (the limiters are in `backend`). TLS, the HTTP redirect and
the split between `ui` and the gateway are done by Traefik (`k8s/base/edge.yaml`).

## Settings

`MICROSERVICES_BACKEND_URI` (default `http://localhost:8081`), `MICROSERVICES_AUTH_URI` (default `http://localhost:9090`).

## Run and test

`./gradlew bootRun`; `./gradlew test` (no external services: `StubService` fakes `backend` and `auth`).
Tests in `src/test/kotlin/net/s4v3/gateway/`: `RoutingTests`, `PassThroughTests`, `HardeningTests`,
`DownstreamFailureTests`, `TargetSettingsTests`, `GatewayConfigurationTests`, `RuntimeTests`; base class `GatewayTest`.

Read on 2026-10-24 from: gateway/src/main/**, gateway/src/test/kotlin/.../GatewayTest.kt, gateway/README.md,
k8s/base/edge.yaml, k8s/base/gateway.yaml, openspec/specs/gateway-hardening/spec.md.
