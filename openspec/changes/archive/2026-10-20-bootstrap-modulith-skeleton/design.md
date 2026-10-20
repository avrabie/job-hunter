# Design

## Context

Greenfield: no code, no git repository yet (`git init` is part of the tasks). Stack and module list are fixed by `PLAN.md` and `openspec/config.yaml`. The repo holds two independent Gradle builds, `backend/` and `auth/`, each with its own wrapper and no shared code; `auth` is a separate change (`bootstrap-auth`), and this change covers only `backend/`. The backend only needs an issuer URI and roles claim, so it does not depend on the auth server's implementation; tests use mocked JWTs. See proposal.md for scope.

## Goals / Non-Goals

**Goals:**
- A booting, secured, tested app where module boundaries and the event registry are enforced by the build.
- A user-provisioning path that is safe under concurrency.
- A pattern for per-user scoping that later modules copy.

**Non-Goals:**
- Any behaviour in modules other than `accounts`.
- Deployment artifacts (Dockerfile, Kustomize, CI) and UI.
- Auth modifications.

## Decisions

**Standalone `backend` build, packages as Modulith modules.** Top-level packages under one root package; `backend/` builds with its own wrapper (`cd backend && ./gradlew build`) and shares nothing with `auth/`, so each can be built, tested, versioned and deployed alone. Alternatives: a root multi-project build (rejected: couples two deployables that share no code), or one Gradle subproject per Modulith module (rejected: Modulith verifies packages, and subprojects add build overhead for a single deployable).

**Public API vs internal.** Each module's root package is its public API; implementation lives in an `internal` subpackage. Alternative: Modulith `@NamedInterface` annotations everywhere. Rejected as noisier for the same effect.

**Koog confinement test.** `ApplicationModules.verify()` does not know third-party libraries, so an ArchUnit rule (no `ai.koog..` imports outside `llm`) is added as a test. This adds ArchUnit as a test dependency. The rule passes trivially until Koog is introduced, but fixes the constraint now.

**Resource server via issuer URI.** `spring.security.oauth2.resourceserver.jwt.issuer-uri` gives discovery, JWKS and `iss` validation. Roles come from a configurable claim name (default `roles`), mapped to `ROLE_*` authorities. Alternative: static JWKS URI. Rejected: loses issuer validation and key rotation by discovery. Startup then needs the issuer reachable; to avoid that in tests, tests use `spring-security-test` JWT post-processors rather than a live issuer.

**Current user via an `accounts` API.** `accounts` exposes a `CurrentUser` service that reads the `Jwt` from the security context and throws when none is present. Other modules never touch `SecurityContext`. The id is the `sub` string, not a numeric key, so no lookup is needed for scoping.

**Provisioning: insert-if-absent.** A filter/interceptor after authentication calls `UserProvisioner`, which runs `INSERT ... ON CONFLICT (sub) DO NOTHING` and publishes `UserRegistered` only when a row was actually inserted. This is atomic, so concurrent first requests produce one row and one event. Alternative: select-then-insert. Rejected: race. Cost: one cheap statement per request; acceptable at this user count, and a short in-memory cache of known subs can be added later without changing behaviour.

**Events.** Spring Modulith `spring-modulith-starter-jdbc` stores the publication table in the baseline migration (schema initialization turned off, Liquibase owns it). `UserRegistered(userId, registeredAt)` is a plain Kotlin data class in the `accounts` public package.

**Database.** Liquibase master changelog with a first SQL changeset (`001-baseline.sql`): `CREATE EXTENSION IF NOT EXISTS vector` (a no-op when the cluster's init script has already created it), `event_publication` (the v2 schema shipped in `spring-modulith-events-jdbc` 2.1.1; the archive table is omitted because completion mode stays `update`), `app_user(sub text primary key, email text, display_name text, created_at timestamptz not null)`. Local and test DB image: `pgvector/pgvector` (pinned tag) via Compose and Testcontainers. The pgvector extension needs a superuser or trusted-extension rights; the dev image allows this, and production DB setup is a later deployment concern.

**Virtual threads.** `spring.threads.virtual.enabled=true`. JDBC pool size remains the real concurrency limit; pool sized explicitly in config.

**Health.** Actuator with liveness/readiness probe groups; readiness includes the DB. Only `/actuator/health/**` is permitted without a token.

**Versions.** The scaffold from start.spring.io pins Spring Boot 4.1.1, Kotlin 2.3.21, Java 25 and Spring Modulith 2.1.1 (BOM in the build); ArchUnit 1.5.1 is pinned from Maven Central. Whether the Modulith JDBC event registry works on these versions is verified by the event-registry test (tasks 3.4).

## Risks / Trade-offs

- [Modulith 2.1.1 with Boot 4.1.1 is untested here] → Initializr selected this pair; the event-registry test (3.4) proves it, and a failure is reported before choosing an alternative.
- [Per-request insert for provisioning] → Cheap upsert now; cache later if measured.
- [Startup depends on the auth server being reachable] → Acceptable for now; document the dev override and use a lazily-resolved decoder only if it proves a problem.
- [Storing email/name is personal data] → Stored only when present in the token, kept out of events and logs; covered by later account deletion/export work.
- [ArchUnit rule is vacuous until Koog arrives] → Includes a small test with a deliberately violating fixture class to prove the rule detects violations.
