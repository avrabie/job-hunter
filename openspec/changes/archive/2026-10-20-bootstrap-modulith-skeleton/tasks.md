# Tasks

## 1. Repository and build

- [x] 1.1 Git repo and root `.gitignore` (build output, IDE files, `private/`) exist; `git check-ignore private/x.pdf` reports it ignored
- [x] 1.2 `backend/` is a standalone Gradle build (own wrapper and settings; `auth/` is independent) scaffolded from start.spring.io (Boot 4.1.1, Kotlin 2.3.21, Java 25, Modulith 2.1.1); `cd backend && ./gradlew compileKotlin compileTestKotlin` succeeds. Scaffold trimmed of Spring AI, Lombok and GraalVM native; ArchUnit added
- [x] 1.3 Verify the `backend` app context starts against the pgvector Testcontainer (the generated `BackendApplicationTests` passes) with the Modulith JDBC starter on the classpath

## 2. Module structure

- [x] 2.1 Create the nine module packages, each with a public root and an `internal` subpackage (a package-info or placeholder only), and verify they compile
- [x] 2.2 Add `ApplicationModules.verify()` test and verify it passes, plus a test asserting the module set is exactly the nine names
- [x] 2.3 Add an ArchUnit test forbidding Koog imports outside `llm`, with a violating fixture proving it fails, and verify both behave as expected
- [x] 2.4 Add a test with a deliberate cycle or internal access (fixture) showing verification fails, and verify the failure names both modules

## 3. Database baseline

- [x] 3.1 Add Testcontainers base test class using the pinned `pgvector/pgvector` image, and verify a trivial test connects
- [x] 3.2 Write the Liquibase master changelog and `001-baseline.sql` changeset (`CREATE EXTENSION IF NOT EXISTS vector`, event publication table, `app_user`) and verify with a test that migrations apply to an empty DB, re-running is a no-op, and a `vector` column can be created
- [x] 3.3 Verify a deliberately broken migration (test-only location) stops startup with an error naming the migration
- [x] 3.4 Add the event publication registry config (Liquibase owns the schema) and verify with a test that an incomplete event is still recorded after the context restarts

## 4. Security and accounts

- [x] 4.1 Configure the resource server (issuer URI from config, roles claim to authorities, `/api/**` authenticated, health permitted) and verify with MockMvc tests: no token 401, wrong issuer 401, expired 401, valid 200
- [x] 4.2 Add role-restricted test endpoint (test sources only) and verify 200 with the role and 403 without
- [x] 4.3 Implement `CurrentUser` in the `accounts` public API and verify it returns the `sub` in a request and fails outside one
- [x] 4.4 Implement `UserProvisioner` (insert-if-absent, email/name claims) and the post-authentication hook, and verify tests: first request creates one row, repeat creates none, claims stored or absent
- [x] 4.5 Publish `UserRegistered(userId, registeredAt)` only on insert, and verify one event on first request, none on repeat, and no email/name in the payload
- [x] 4.6 Add a concurrency test with parallel first requests for one `sub` and verify exactly one row and one event, with no request failing
- [x] 4.7 Add a test-only per-user resource and verify user A gets 404 for B's record and A's list contains only A's records (the pattern later modules copy)
- [x] 4.8 Verify with a log-capture test that a rejected token value never appears in logs

## 5. Runtime and local dev

- [x] 5.1 Enable virtual threads, set the JDBC pool size explicitly, and verify the app starts and a request runs on a virtual thread
- [x] 5.2 Configure actuator liveness/readiness with DB in readiness and verify: healthy 200 without token, DB down 503 on readiness and 200 on liveness, other actuator endpoints 401 or 404
- [x] 5.3 Fail startup when the issuer URI is missing outside the test profile, and verify the message names the setting
- [x] 5.4 Add `docker-compose.yml` (pgvector Postgres), a `dev` profile with env-based settings and no committed credentials, and verify `docker compose up -d` plus `./gradlew bootRun` starts the app; the dev profile sets `server.port=8081` so the gateway can use 8080 (`bootstrap-gateway`)
- [x] 5.5 Write README with the local run and test commands, and verify them from a clean checkout as written

## 6. Integration check

- [x] 6.1 Run the full `./gradlew build` from clean and verify all tests pass, and spot-check each spec scenario in this change maps to a passing test
