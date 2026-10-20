# Proposal

## Why

The edge sends `/api` and `/auth` to a Spring Cloud Gateway that fans them out to `backend` and `auth` (PLAN.md section 2, decided). The `gateway/` build is only a scaffold. This change makes it a small, stateless router with the settings that sample-shop's gateway learned the hard way (connection pool lifetime, forwarded headers), so the whole request path can run locally and in the cluster.

## What Changes

- Add the gateway routes: `/api/**` to `backend` and `/auth/**` to `auth`, with no path rewriting, so `auth` keeps its `/auth` context path and `backend` sees `/api/...`.
- Pass the `Authorization` header and request and response bodies through untouched. The gateway validates no token, authorizes nothing and keeps no session or state; each service enforces its own access rules.
- Pass the original host and scheme to `auth` so its redirects and login page use the public URL.
- Add response security headers through the gateway's built-in secure-headers filter, with the content security policy turned off (see design), without adding Spring Security.
- Set connect and response timeouts and a bounded connection pool (idle and lifetime limits) so a deploy of a downstream service does not cause lingering 500s.
- Expose liveness and readiness endpoints that do not depend on `backend` or `auth`, read target addresses from environment variables, and add tests and a README for running the three services together locally on port 8080.

**Explicitly out of scope** (later changes): token validation or any authorization at the gateway; rate limiting and bot protection; retries and circuit breakers; CORS (the browser sees one origin); request size limits; a route for `/` or the UI; TLS (terminated at the edge); metrics, tracing and access logging beyond what the framework logs; Dockerfile, CI and Kubernetes manifests (`bootstrap-k8s`); any change to `backend` or `auth` code.

## Capabilities

### New Capabilities
- `gateway-routing`: which paths go to which service, what is forwarded unchanged, what happens for unknown paths and failing downstream services.
- `gateway-hardening`: response security headers, timeouts, connection pool limits and what the gateway must never log.
- `gateway-runtime`: health endpoints, configuration from the environment, ports, and the reproducible local run and tests.

### Modified Capabilities
<!-- None: no main specs exist for the gateway yet. -->

## Impact

- **Modules touched:** none of the backend's Modulith modules. This is the separate `gateway/` build, a Kotlin Spring Boot application on Spring Cloud Gateway (reactive, Spring Cloud 2025.1.3).
- **New code:** `gateway/src/main`, `gateway/src/test`, `gateway/application.yaml` settings, `gateway/README.md`.
- **Dependencies:** none beyond the scaffold (gateway server WebFlux starter, actuator). Tests use Reactor Netty, already on the classpath, to run stub downstream servers. Spring Security is deliberately not added.
- **Ports and names:** the gateway listens on 8080; `backend` is expected on 8081 and `auth` on 9090 when run locally. Targets are set with `MICROSERVICES_BACKEND_URI` and `MICROSERVICES_AUTH_URI`, which `bootstrap-k8s` sets to the in-cluster service addresses.
- **Personal data:** request and response bodies and bearer tokens pass through memory only. Nothing is stored, and tokens and request bodies must not be logged.
- **Per-user scoping:** unchanged; the gateway adds and removes no identity information, so isolation between users stays the services' responsibility.
- **Claude cost:** none.
- **External systems:** none. The edge (Gateway API, Traefik) in front of it is covered by `bootstrap-k8s`.
