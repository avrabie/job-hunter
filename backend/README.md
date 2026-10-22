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
./gradlew bootRun --args='--spring.profiles.active=dev'
```

`spring-boot-docker-compose` (development only, not in the image) starts `docker-compose.yml` itself: PostgreSQL
with pgvector on localhost:5432 with a local-only password, and the app connects without any setting. From the IDE,
run `BackendApplication` with the working directory `backend/` and the active profile `dev`. Optional overrides
(for example `ISSUER_URI`) go in `.env` (see `.env.example`).

The app listens on **http://localhost:8081** (8080 is reserved for the gateway when all services run together).
Liquibase creates the schema on startup.

```bash
curl http://localhost:8081/actuator/health/readiness   # {"status":"UP"}
curl -i http://localhost:8081/api/anything             # 401: every /api/** call needs a bearer token
```

Stopping the app stops the container (data stays in the volume; `docker compose down -v` deletes it). PostgreSQL
reads its password only when it creates the volume, so after changing `DB_PASSWORD` run `docker compose down -v`.

### Settings

| Setting | Where | Default (dev profile) |
|---|---|---|
| `DB_PASSWORD` | environment | `jobhunterdev` (local compose only) |
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
- Module events are specified as contracts in `openspec/`; `accounts` publishes `UserRegistered(userId, registeredAt)` and
  `postings` publishes `JobIngested(postingId, ingestedAt)`.

## Postings (for source adapters)

A source adapter maps what a site delivers to a `PostingCandidate` and calls `PostingIngest.ingest`. The shape below is
the one `PostingIngestTests` uses.

```kotlin
val result = postingIngest.ingest(
    PostingCandidate(
        sourceId = "example-board",          // stable id of the source
        sourcePostingId = "12345",           // the source's own id for the ad
        url = "https://example.org/jobs/12345",
        title = "Senior Kotlin Developer (m/w/d)",
        company = "Acme AG",
        description = text,
        rawPayload = body,                   // stored unchanged, for re-parsing
        city = "Zurich",                     // null if unknown; without a city only same-source dedupe applies
        workloadText = "80-100%",            // free text, parsed to a range; null if absent
    ),
)
// result.outcome is CREATED, UPDATED or UNCHANGED; result.postingId is the canonical posting
```

- Calls are idempotent per (`sourceId`, `sourcePostingId`) and safe to repeat. A blank required field throws
  `PostingRejectedException` and stores nothing. Run sources one after another: the lock covers one source posting, not the
  cross-source dedupe key.
- The same ad on another source is linked to the existing canonical posting (outcome `CREATED`, no `JobIngested`).
  Only the source that created a posting may change its fields.
- The language is detected on ingest. French, Italian and undetected postings are stored and flagged `outOfScope`.
- Other modules read through `PostingQuery`; it never returns the raw payload.

## Sources (Greenhouse boards)

`AtsCrawl.run()` crawls the configured Greenhouse boards one after the other: it reads each board's light job list, keeps
Swiss IT jobs, fetches their details and ingests them through `PostingIngest`. It returns one `BoardResult` per board
(`COMPLETED` with counts, `NOT_MODIFIED`, `TOO_SOON`, `BLOCKED_BY_ROBOTS`, `FAILED`, `ALREADY_RUNNING`). Nothing triggers
it yet except the stopgap below; the scheduler comes with `ingestion-scheduler`.

| Setting (`jobhunter.sources.*`) | Default |
|---|---|
| `user-agent` | `JobHunter/0.1 (+https://s4v3.net)` |
| `pause` | `PT1S` between requests to one host |
| `min-interval` | `PT15M` between two runs of one board |
| `connect-timeout`, `read-timeout` | `PT5S`, `PT30S` |
| `max-list-bytes`, `max-job-bytes` | 8 MB, 1 MB |
| `greenhouse.base-url` | `https://boards-api.greenhouse.io` |
| `greenhouse.boards` | `proton, getyourguide, scandit, databricks, elastic, mongodb, gitlab, datadog, grafanalabs, ripple` |
| `greenhouse.it-keywords`, `greenhouse.excluded-keywords` | whole words matched against the job title; the excluded ones win |
| `greenhouse.run-on-startup` | `false` |

- **Boards:** the seed boards answered and had Swiss roles on 2026-10-22. To add one, check
  `GET https://boards-api.greenhouse.io/v1/boards/<token>/jobs` first, then add the token to `greenhouse.boards`
  (environment variable `JOBHUNTER_SOURCES_GREENHOUSE_BOARDS`, comma separated). Lever and other systems are not supported.
- **Politeness:** robots.txt is read once per host per crawl, the list is fetched with `If-None-Match` (the validator lives
  in `source_fetch_state`), and a board is not run again within `min-interval`. A board where a job detail failed keeps no
  validator, so the next run reads it in full.
- **Stopgap run:** `JOBHUNTER_SOURCES_GREENHOUSE_RUN_ON_STARTUP=true` starts one crawl after startup, on its own thread. Turn it off
  again after checking the logged per-board counts.
- **Limits:** salary, workload and remote mode are left unknown; the IT filter is a title heuristic (pre-sales titles such as
  "Solutions Architect" pass); jobs that leave a board are not closed.
- Logs hold board tokens, outcomes, counts and HTTP statuses only, never job text or response bodies.

## Logging and personal data

The profile is personal data and nothing in the application logs it; posting text and raw payloads are not logged either. The frameworks do, though, below INFO: Spring MVC
prints the response object at `DEBUG` (`org.springframework.web`), and Spring JDBC (`org.springframework.jdbc.core`) and the
PostgreSQL driver (`org.postgresql`) print every bound SQL parameter at `TRACE`. Never enable those loggers below INFO
outside a throwaway local run, and never in the cluster.

## Health

`/actuator/health/liveness` and `/actuator/health/readiness` need no token. Readiness includes the database;
liveness does not, so a database outage does not restart the pod. No other actuator endpoint is exposed.
