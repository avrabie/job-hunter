# backend

The job-hunter modular monolith: Kotlin, Spring Boot 4, Spring Modulith, Spring MVC + JDBC on virtual
threads, PostgreSQL with pgvector, Liquibase. It is an OAuth2 resource server: it accepts JWTs issued by
the `auth` service and creates a user record the first time it sees a new `sub`.

Roadmap and decisions: [`../PLAN.md`](../PLAN.md). Requirements: `../openspec/`.

## Prerequisites

- JDK 25
- Docker (local database and the test suite)

## Run locally

```bash
cd backend
cp .env.example .env            # then set DB_PASSWORD in .env (git-ignored)
docker compose up -d            # PostgreSQL with pgvector on localhost:5432
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The app listens on **http://localhost:8081** (8080 is reserved for the gateway when all services run together).
Liquibase creates the schema on startup.

```bash
curl http://localhost:8081/actuator/health/readiness   # {"status":"UP"}
curl -i http://localhost:8081/api/anything             # 401: every /api/** call needs a bearer token
```

Stop with `Ctrl+C`, and `docker compose down` for the database (add `-v` to delete its data).

PostgreSQL only reads `DB_PASSWORD` when it first creates its data volume. If you change the password later,
run `docker compose down -v` (this deletes the local data) and start again, otherwise the app fails with
"password authentication failed".

### Settings

| Setting | Where | Default (dev profile) |
|---|---|---|
| `DB_PASSWORD` | `.env` | required, no default |
| `DB_PORT` | `.env` | `5432` |
| `ISSUER_URI` | `.env` | `http://localhost:8080/auth`, the auth service behind the gateway |

Outside the `dev` profile the issuer is read from `spring.security.oauth2.resourceserver.jwt.issuer-uri`
(environment variable `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`). The application refuses to
start without it. The issuer is contacted lazily, so the backend can start before `auth` is up.

## Tests and build

```bash
./gradlew test      # needs Docker: tests start PostgreSQL (pgvector) containers
./gradlew build     # compiles, runs the tests, builds the jar
```

Tests use a mock issuer (a local HTTP server that serves the discovery document and signing keys), so no
auth service is needed.

## Structure

Nine modules, one top-level package each under `net.s4v3.backend`: `sources`, `postings`, `accounts`,
`profile`, `matching`, `applications`, `llm`, `web`, `notifications`.

- A module's **root package is its public API**; everything in its `internal` subpackage is private to it.
- Modules talk to each other only through that API or through events. `ModularityTests` runs
  `ApplicationModules.verify()` and fails the build on a violation or a dependency cycle.
- Only the `llm` module may use Koog; only `accounts` may read the token or the security context. Other
  modules ask `CurrentUser` who the caller is. Both are enforced by ArchUnit tests.
- Every query on per-user data is scoped by the current user's id (the token's `sub`). `PerUserIsolationTests`
  shows the pattern: a record of another user answers 404, exactly like one that does not exist.
- Database changes are Liquibase changesets under `src/main/resources/db/changelog/`.
- Module events are specified as contracts in `openspec/`; `accounts` publishes `UserRegistered(userId, registeredAt)`.

## Health

`/actuator/health/liveness` and `/actuator/health/readiness` need no token. Readiness includes the database;
liveness does not, so a database outage does not restart the pod. No other actuator endpoint is exposed.
