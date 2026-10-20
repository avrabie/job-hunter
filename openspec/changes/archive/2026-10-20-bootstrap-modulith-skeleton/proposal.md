# Proposal

## Why

There is no code yet. Every later phase (ingestion, matching, generation, UI) needs a runnable, secured Spring Boot modular monolith with the nine module boundaries, a database with migrations, and a way to identify the current user from the auth service JWT. Fixing these foundations first means later changes only add behaviour inside existing modules, and the module-boundary and per-user-scoping rules are enforced from the first commit.

## What Changes

- Add a standalone Gradle (Kotlin DSL) build in `backend/` (the modulith; `auth/` is a separate, independent build added by a later change): Kotlin 2.x, JVM 21+, Spring Boot 4, Spring Modulith, MVC + JDBC on virtual threads. Versions are pinned from start.spring.io / Maven Central at implementation time.
- Create the nine top-level module packages (`sources`, `postings`, `accounts`, `profile`, `matching`, `applications`, `llm`, `web`, `notifications`), each with a public API package and an internal package, and a test that runs `ApplicationModules.verify()`.
- Enable the Modulith JDBC event publication registry so module events survive restarts.
- Add PostgreSQL + pgvector with Liquibase; the baseline changelog creates the `vector` extension, the event publication table, and the `accounts` user table.
- Configure the backend as an OAuth2 resource server (issuer URI + JWKS, roles claim). All `/api/**` requires a valid JWT; health endpoints stay open.
- In `accounts`: expose the current user (`sub`) to other modules, and create a user record the first time a valid `sub` is seen. This covers users who sign in with Google at the auth server for the first time.
- Add a local dev setup (Docker Compose Postgres with pgvector, a dev profile, README steps) and a CI-runnable build and test.
- Add a repo-level `.gitignore` that excludes `private/` (real CVs and letters).

**Explicitly out of scope** (separate changes): Kustomize/Kubernetes manifests, Dockerfile and CI image publishing, the auth service itself (separate change `bootstrap-auth`), the React UI, any source adapters, scoring, Koog or Claude integration, BYO-key handling, quotas, account deletion/export endpoints, the notifications digest. Module packages other than `accounts` contain only their package structure and no behaviour.

## Capabilities

### New Capabilities
- `accounts`: validating the auth service JWT, resolving the current user from `sub`, provisioning a user record on first sight, and the rule that per-user data is always scoped by that user id.
- `module-boundaries`: the nine-module layout, the rule that modules interact only through public APIs or events, build-time verification, and the durable event publication registry.
- `platform-runtime`: the database baseline (Postgres, pgvector, Liquibase), health endpoints, virtual-thread servlet runtime, and the reproducible local dev and test environment.

### Modified Capabilities
<!-- None: no main specs exist yet. -->

## Impact

- **New code:** whole repository (`backend/` with its Gradle build, `src/main` and `src/test`, `docker-compose.yml`, `.gitignore`).
- **Dependencies (new):** Spring Boot starters (web, jdbc, security, oauth2-resource-server, actuator, validation), Spring Modulith (core, events-jdbc, test), Liquibase, PostgreSQL driver, Testcontainers (Postgres/pgvector), a JWT test helper (spring-security-test). Kotlin Spring plugin and Jackson Kotlin module.
- **Personal data:** the only personal data stored is the `sub` and (if present in the token) email and display name in the `accounts` user table. Logs must not contain tokens. No CV content exists yet.
- **Per-user scoping:** this change establishes the current-user mechanism and the scenario pattern (user A cannot read user B) that later per-user capabilities reuse.
- **Claude cost:** none. No LLM calls are introduced.
- **External systems:** depends on an auth server (built in `bootstrap-auth`) being reachable for issuer discovery/JWKS; tests use a mocked issuer, not the live server.
