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

Twelve modules, one top-level package each under `net.s4v3.backend`: `sources`, `postings`, `accounts`,
`profile`, `matching`, `cv`, `files`, `llm`, `web`, `applications`, `jobimport`, and the placeholder `notifications` (an empty
`package-info.java` each, until they are built).

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

### Where things live

The module `web` holds the HTTP controllers of profile (`ProfileController`), postings (`PostingsController`),
repositories (`RepositoriesController`) and project analysis (`ProjectAnalysisController`). Its `web/internal/` also has
`AnalysisLimiter`, `AnalysisSettings`, `ErrorResponses` and `OpenApiConfig`. The name is historical: `web` is not every HTTP
endpoint, the CV and file endpoints live in their own modules. The module `cv` holds CV extraction (`cv/internal`, with the
export and public-CV controllers beside it there), CV export (`cv/export`: the document model, the renderers and the export
service) and the public CV (`cv/publiccv`).

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
### Crawl schedule

A scheduler starts the crawl by itself. The schedule is global, off by default, and stored in the database once an
administrator sets it (table `crawl_schedule`, one row); until then the settings give it.

| Setting (`jobhunter.sources.schedule.*`) | Default |
|---|---|
| `enabled` | `false` (the prod overlay sets `JOBHUNTER_SOURCES_SCHEDULE_ENABLED=true`) |
| `cron` | `0 0 * * * *`: every hour on the hour (Spring cron, six fields) |
| `zone` | `Europe/Zurich` |
| `tick` | `PT30S`: how often the stored schedule is read; a change applies within one tick |

- **Why hourly:** a run where nothing changed costs about ten `304` list requests; a changed board costs about a minute.
  Each board still waits `min-interval` (15 minutes) between runs, and the API refuses a cron that fires faster.
- **Behaviour:** a run that is still going when the next is due is skipped, not queued; a failed run is logged by exception
  type and the schedule keeps firing; a time missed while the pod was down, or before a schedule change, is not made up.
  Each run logs a start line and an end line with board counts per outcome and job totals, never job text.
- **API** (needs an access token with the `ADMIN` role, otherwise 401 or 403; see the auth README for how a user becomes
  admin):

```sh
curl -H "Authorization: Bearer $TOKEN" https://s4v3.net/api/admin/crawl-schedule
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":true,"cron":"0 0 * * * *","zone":"Europe/Zurich"}' https://s4v3.net/api/admin/crawl-schedule
```

  The answer holds `enabled`, `cron`, `zone`, `nextFire` (absent when disabled) and `updatedAt`. An invalid cron or zone, a
  missing field, or a cron that fires more often than every 15 minutes gets a 400 `{"error":"validation_failed","field":...}`
  and changes nothing. The row keeps the administrator's token `sub`; the log line for a change holds `enabled` and `cron` only.
- Several replicas would each fire; the per-board locks and the minimum interval keep the crawl from running twice.

- **Stopgap run:** `JOBHUNTER_SOURCES_GREENHOUSE_RUN_ON_STARTUP=true` starts one crawl after startup, on its own thread. Turn it off
  again after checking the logged per-board counts.
- **Limits:** salary, workload and remote mode are left unknown; the IT filter is a title heuristic (pre-sales titles such as
  "Solutions Architect" pass); jobs that leave a board are not closed.
- Logs hold board tokens, outcomes, counts and HTTP statuses only, never job text or response bodies.

## Postings API

`GET /api/postings?page=&size=&q=&canton=&source=` lists the crawled postings, newest first-seen first (any signed-in user;
postings are shared): `[{id, title, company, city, canton, language, firstSeen, lastSeen, links:[{sourceId, url, primary}]}]`.
`q` is a case-insensitive part of the title or company (`%` and `_` are literal), `canton` a two-letter code, `source` a
source id; `size` defaults to 20 and is capped at 100; a negative page, a bad canton or a bad source id give 400.
`GET /api/postings/{id}` adds `description` (plain text) and answers 404 for an unknown id. `links` holds one entry per
source that delivered the ad, the primary source first. Neither endpoint returns the raw payload, and the search text is
not logged by the application.

## Matching (prefilter)

For every user with a saved profile the backend decides which shared postings are worth a closer look. It is a
deterministic prefilter: no Claude call, no embeddings. Results are stored per user and posting in `posting_match`
(passed, score 0-100, reason codes, the profile version used) and are deleted with the account.

- **Hard rules** (a failed rule rejects the posting): out of scope language, a known canton not among the preferred
  cantons, a known workload range that does not overlap the user's, a known remote mode the user does not accept, a known
  top salary below the user's minimum. A value the posting does not state never rejects it; it is recorded as
  `UNKNOWN_CANTON`, `UNKNOWN_WORKLOAD`, `UNKNOWN_REMOTE` or `UNKNOWN_SALARY`. Work authorisation is not used.
- **Score:** up to 50 for the title against the target roles (or past job titles when none are set), up to 40 for the
  user's skills found as whole words in the title or description (five or more give 40), 10 for a posting language the
  user speaks at B2 or above. A posting passes when no hard rule failed and the score reaches
  `jobhunter.matching.threshold` (default 30; env `JOBHUNTER_MATCHING_THRESHOLD`).
- **When it runs:** a new posting (`JobIngested`) is evaluated for every user with a profile; a saved profile
  (`ProfileSaved`) re-evaluates all postings for that user. Both are idempotent and retried by the event registry.
  Profiles saved before this feature existed are only evaluated when their owner saves again.
- **Reasons** are fixed codes (`ROLE_MATCH`, `SKILL_MATCH_<n>`, `LANGUAGE_MATCH`, `CANTON_MISMATCH`, `BELOW_THRESHOLD`, ...);
  no profile or posting text is stored or logged, and logs never put a user id next to a score.
- **API:** `GET /api/matches?page=0&size=20` (size at most 100) returns the caller's passing matches, best score first:
  `[{postingId, title, company, city, canton, url, score, reasons}]`. It needs an access token and returns only the
  token owner's results.

## API description (OpenAPI)

The backend describes its REST API itself: `GET /api/openapi.json` is the OpenAPI 3 document and `GET /api/docs` is a
browsable page (Swagger UI; it redirects to `/api/swagger-ui/index.html`). Both are generated from the controllers, are
readable without a token (they hold shapes, never data) and sit under `/api`, so the gateway and edge forward them as
they are. To try a call from the page, sign in, copy an access token, press **Authorize** and paste it.

- **Switch:** `jobhunter.api-docs.enabled` (default `true`; env `JOBHUNTER_API_DOCS_ENABLED`). When `false` neither path
  exists: an authenticated request gets 404, an anonymous one 401 like any other protected path.
- **New endpoints must be documented:** give every controller method `@Operation(summary = ...)` and its `@ApiResponse`s
  (and a `@Tag` on the controller). `ApiDocsContentTests` compares every request mapping under `/api` with the document and
  fails the build, naming the endpoint, if one is missing or has no summary. Examples in the annotations stay synthetic.
- **Content security policy:** the gateway currently has the policy header switched off (the auth login page needs it). If
  it is switched on later, Swagger UI's inline script needs an exception for `/api/swagger-ui/`.
- Only the application's own endpoints are described; the health endpoints and the auth service are not part of it.

## Logging and personal data

The profile is personal data and nothing in the application logs it; posting text and raw payloads are not logged either. The frameworks do, though, below INFO: Spring MVC
prints the response object at `DEBUG` (`org.springframework.web`), and Spring JDBC (`org.springframework.jdbc.core`) and the
PostgreSQL driver (`org.postgresql`) print every bound SQL parameter at `TRACE`. Never enable those loggers below INFO
outside a throwaway local run, and never in the cluster. The same holds for Spring Security's `DEBUG` and `TRACE` output, which
prints request URLs including the query string (for example a posting search text).

## Health

`/actuator/health/liveness` and `/actuator/health/readiness` need no token. Readiness includes the database;
liveness does not, so a database outage does not restart the pod. No other actuator endpoint is exposed.

## Profile content

The profile document holds `location`, `workAuthorisation`, `languages`, `summary`, `experience`, `education`, `publications`,
`codeAccounts`, `openSourceProjects`, `skills` and `preferences`. Everything is one JSONB document per user in `profile.document`, so new fields need no
migration: a document without them reads with empty lists.

- A **publication** has a required `title` and optional `venue`, `year` (1900 to next year), `authors` (free text) and `url`
  (absolute `http` or `https` only); at most 100.
- Each **experience** entry has `technologies`, a list of short tags (not blank, at most 60 characters, at most 30 per list).
  The server never changes tags; the page and the CV reader tidy them (trim, drop duplicates that differ only in case).
- A **code account** is a `provider` (`GITHUB` for now) and a `username` (GitHub rule: 1 to 39 letters, digits and single hyphens,
  not starting or ending with a hyphen); at most one per provider.
- An **open source project** is a repository the user picked, stored as a snapshot: `provider`, `fullName` (`owner/repo`),
  `name`, an `https` `url`, optional `description`, `language`, `topics`, `stars` and the user's own `note`; unique per
  provider and `fullName`, at most 30, in the user's order. The old `projects` list is gone: stored values are ignored on
  read and dropped on the next save, and a request that sends `projects` is rejected.
- The request body limit is **256 KB** (413 before anything is parsed).

The CV draft (`llm` module) returns technologies per position and no projects (`section=projects` answers 400). A tag longer
than 60 characters is dropped.

### CV export

`POST /api/cv/export` (token required) renders the caller's **saved** profile as a CV: `{"format":"PDF","template":"MODERN","language":"DE",
"sections":[...],"includeOpenSource":true,"includeAnalysis":false,"includeWorkAuthorisation":true}`. `format` is `MARKDOWN`, `HTML` or
`PDF` (A4, selectable text, fonts embedded); `template` is `CLASSIC` (single column, ATS-safe), `MODERN` (two columns, accent colour) or
`COMPACT` (dense, aims at one page); `language` is `EN` or `DE` (Swiss spelling, no `ß`). `sections` selects and orders `PROFILE`,
`EXPERIENCE`, `OPEN_SOURCE`, `EDUCATION`, `SKILLS`, `LANGUAGES`, `PUBLICATIONS` (default: all, in that order, empty ones left out). The
body never carries profile content and an unknown field is a 400 `invalid_request`. It is deterministic (no model, no cost) and prints only
what the profile holds: preferences, salary and cantons are never printed, and the user's own text is never translated. It needs
`contact.fullName` in the saved profile (409 `name_required`). Limits: `jobhunter.cv-export.per-hour` (30) per user, counted before any
rendering (429 `too_many_exports`); at most 2 PDF renders at once, a third waits 5 s and then gets 503 `busy`; 500 `render_failed`.
Profile text is escaped before it enters HTML, links are emitted only for http(s), the HTML carries a CSP that forbids scripts and outside
resources, and the PDF renderer makes no network request. Nothing of the profile is logged. See `docs/cv-export.md` for the pipeline, the
dependency and the font licences. The profile's `contact` block (full name, headline, email, phone, website, LinkedIn) is validated like
the other fields (`contact.email` and so on) and stored only in the profile document.

### Public CV

The owner can publish an opt-in public page of the CV at a handle they choose (`https://s4v3.net/cv/<handle>`). Everything is
off until the owner ticks it. The rules are in `cv.publiccv` (`PublicCvService`, `internal/HandleRules`); the endpoints are in
`cv/internal` (`PublicCvController`, `PublicCvPageController`, `PublicCvAdminController`). The owner's and the administrator's
guide is `docs/cv-public.md`.

**Owner endpoints** (token required; everything is by the token's `sub`):

| Endpoint | Answers | Errors |
|---|---|---|
| `GET /api/cv/public` | `{"configured":false}` or the state: `handle`, `published`, `locked`, `settings`, `handleChangeableAt`, `updatedAt` | 401 |
| `PUT /api/cv/public`, body `{handle, published, settings}` | the state. `handle` and `published` are required; `settings` defaults to all off | 400 `validation_failed` (`fields.handle`) or `invalid_request` (unknown field or value); 409 `handle_taken`, `handle_reserved`, `handle_held`, `handle_change_too_soon`, `name_required`, `locked`; 429 |
| `GET /api/cv/public/handle?handle=` | `{"available":true}`, or `{"available":false,"reason":"invalid"\|"reserved"\|"taken"\|"held"}`. The owner's own handle is available | 400 `invalid_request` (no handle); 429 |
| `POST /api/cv/public/preview`, body `{settings}` | `{"path":"/api/cv/public/preview/<ticket>"}` | 400 `invalid_request`; 429 |
| `DELETE /api/cv/public` | 204, also when there is no page; the handle is held 90 days | 409 `locked` |

A 429 is `{"error":"rate_limited"}` with `Retry-After`.

**Public endpoints** (no token):

- `GET /api/public/cv/{handle}`: 200 HTML; 304 for a matching `If-None-Match`; otherwise one uniform 404 `{"error":"not_found"}`
  for every handle that is not served (unknown, paused, removed, locked, never published, owner without a full name, malformed).
  429 `rate_limited` as above.
- `GET /api/cv/public/preview/{ticket}`: the owner's preview, once, then 404. The ticket is the credential (128 random bits).

**Administrator endpoints** (ADMIN role, through the `/api/admin/**` rule):

- `DELETE /api/admin/public-cv/{handle}`: takedown. Sets `published = false` and `locked = true`, so the owner cannot publish
  again. 204, or 404 `not_found` for an unknown handle; 403 for a non-administrator.
- `POST /api/admin/public-cv/{handle}/unlock`: lifts the lock. The page stays paused until the owner publishes it. 204 or 404.

**Settings** (`PublicCvSettings`, the JSON of the owner's endpoints):

```
{"template":"CLASSIC|MODERN|COMPACT","language":"EN|DE","sections":["EXPERIENCE",...],"headline":false,"analysis":false,
 "workAuthorisation":false,"location":false,"contact":{"email":false,"phone":false,"website":false,"linkedin":false,
 "codeAccounts":false},"indexable":false}
```

Defaults: `template` `CLASSIC`, `language` `EN`, `sections` empty, every flag and every contact field false. The full name is
always printed (the page needs it; without it the page is not served). Each other field is printed only when ticked. `OPEN_SOURCE`
in `sections` includes the projects with their notes and technologies; `analysis` adds the analysis summaries; `indexable` allows
search engines. Duplicate sections are kept once, at their first position.

**Never printed:** preferences (target roles, workload, cantons, salary), anything unticked, the profile document, the file
cabinet, the stored CV. `CvBuilder` in `cv.export` decides what is printed for both export and the public page.

**Handle rules** (`HandleRules`):

- The input is lower-cased first. 3 to 30 characters, `[a-z0-9]` with single hyphens between them (no leading, trailing or
  doubled hyphen). Not only digits. Violations are 400 `validation_failed` with `fields.handle`, a fixed message that never
  repeats the value.
- Reserved words (`HandleRules.RESERVED`, for example `admin`, `api`, `cv`, `profile`, `publish`, `public`, `s4v3`, `undefined`)
  are 409 `handle_reserved`.
- Unique across users, compared case-insensitively (unique index on `lower(handle)`); one handle per user. Taken: 409 `handle_taken`.
- A change is allowed once every 30 days. The first claim is not a change. The rule reads the audit table (`HANDLE_CHANGED`),
  so deleting and claiming again does not reset it. Too soon: 409 `handle_change_too_soon`, with the date in `handleChangeableAt`.
- A released handle (after a change or a removal) is **held for 90 days**: another user gets 409 `handle_held`; the owner can take
  it back. The hold rows are purged once a day at 03:45 (`PublicCvMaintenance`, one query, only a count is logged).
- Publishing while the profile has no full name is 409 `name_required`. A save under an administrator's lock that publishes is 409 `locked`.

**Live rendering and caching.** The page is rendered from the owner's stored profile, not from a copy, so a saved profile edit is
public at once in the ticked fields. Rendered pages are cached in memory (`RenderCache`, at most 500 entries, 10 minutes) under the
key (owner, profile version, settings version, template, language): a save changes a version and the next request renders again.
A paused, locked or removed page is never served from the cache (the state is checked first). The cache and the limiters live in
each backend process.

**Headers** of the public page:

- `Content-Type: text/html; charset=utf-8`; `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src data:;
  base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox`.
- `X-Robots-Tag: noindex, nofollow, noarchive, nosnippet` (or `index, follow` when `indexable`); the HTML has a matching robots meta.
- `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-cache`, `ETag` (quoted SHA-256 of the HTML,
  hex).
- The preview: `Cache-Control: no-store`, `X-Robots-Tag` always `noindex`. Every 404 and 429 carries `no-store`, `noindex`,
  `nosniff` and `no-referrer`.
- The page has no script. Links are http(s) only, with `rel="noopener noreferrer nofollow"`.

**Rate limits** (`jobhunter.public-cv.*` in `application.yaml`, defaults in brackets):

- `page-per-ip-per-minute` (60): page requests per client IP.
- `not-found-per-ip-per-minute` (10): answers that are 404, including malformed handles; the stricter limit.
- `page-per-handle-per-minute` (600): page requests for one handle, all clients together (a backstop when the IP is wrong).
- `handle-checks-per-hour` (60) and `previews-per-hour` (30): per user, for the owner's endpoints.

Previews have no IP limit: their tickets are random, one-use and valid for 60 seconds (`PreviewTickets`).

**Client IP.** The first value of `X-Forwarded-For`, trimmed, when it is 1 to 45 characters; otherwise the socket address. The
backend does not configure a forwarded-header strategy and the edge's setting is not in this repository (`k8s/platform/traefik-values.yaml`
has no forwarded-header key), so the IP limit is best effort; the per-handle cap is the backstop.

**Audit table** `public_cv_audit` (`owner_sub`, `event`, `occurred_at`, `flags` JSON): events `PUBLISHED`, `PAUSED`,
`SETTINGS_CHANGED`, `HANDLE_CHANGED`, `REMOVED`, `TAKEN_DOWN`, `UNLOCKED`. `flags` says which settings are on (for example
`contact.email`, `section.SKILLS`, `indexable`) and never their values. It has no handle. Takedown and unlock store empty flags.
Rows are deleted with the account.

**Retention.** `public_cv` (settings, handle, flags), `public_cv_handle_hold` and `public_cv_audit` reference `app_user` with
`ON DELETE CASCADE`, so deleting an account removes its page, its holds and its audit rows; the handle is then free at once.
Liquibase changesets `moldo:012-public-cv`, `013-public-cv-handle-hold`, `014-public-cv-audit` (file `009-public-cv.sql`).

**Logging.** A public page answer logs one line: status, duration and the first 8 characters of the ETag. The owner and
administrator endpoints log nothing. No line holds a handle together with an address, profile text, a setting value or a ticket.

### Project analysis

`POST /api/profile/open-source-projects/analyze` with `{"provider":"GITHUB","fullName":"owner/repo"}` (token required) analyses one
repository of the caller's **saved** profile (404 `project_not_found` otherwise, so it cannot be used on arbitrary repositories). It
reads the file tree (one GitHub API call, at most 400 paths to depth 3), the README (20 KB) and up to 8 build files (12 KB each, such as
`build.gradle.kts`, `package.json`, `pom.xml`) from `raw.githubusercontent.com` (`jobhunter.repositories.github.raw-base-url`), asks the
model for a summary, an architecture description and the technologies, and returns them without saving anything. A technology is kept
only when the file it cites is in the repository's tree, so an answer cannot invent evidence, and repository text is passed to the
model as quoted data with no tools. Limited to `jobhunter.analysis.per-hour` (10) per user, counted before the model is called, with
its own counter. Failures: 400 `invalid_request`, 404 `repository_not_found`, 429 `too_many_analyses` or `provider_rate_limited`,
502 `provider_unavailable` or `analysis_failed`, 503 `analysis_not_configured` (no API key). The client stores the analysis on the
project and adds the technologies to the skills: a skill has an `origin` (`MANUAL` or `ANALYSIS`) and `sources` (repository, link and
the file that shows it), so every analysis skill is traceable; the server only checks their shape (at most 20 sources per skill, 200
skills in all). Nothing of the repository, the model's answer or the repository name is logged.

### Repository listing

`GET /api/profile/repositories?provider=GITHUB&username=<name>` (token required) lists the public, non-fork, non-archived
repositories of that account, newest push first, at most 100 (one page), for the page's checklist. It reads and saves
nothing of the user's. The username is checked before any outside call and the GitHub address comes from configuration
(`jobhunter.repositories.github.base-url`, default `https://api.github.com`; timeouts and body cap beside it), so a request
cannot reach another host. Answers are cached for 5 minutes per account because GitHub's unauthenticated limit (60 requests
an hour per egress address) is shared by all users. Failures: 400 `invalid_username` or `unknown_provider`, 404
`account_not_found`, 429 `provider_rate_limited`, 502 `provider_unavailable`. Nothing from the request or the answer is
logged. A new provider is one `RepositoryProvider` implementation plus a `CodeProvider` value and its username rule in
`UsernameRules.kt`.

## File cabinet (files module)

Each user has any number of files, with no count limit and no quota. A file is of kind `CV` (PDF, Markdown `.md` or plain text
`.txt`, at most 5 MB, type decided from the content: `%PDF-`, or valid UTF-8 without NUL bytes) or `OTHER` (any content and
name, at most 10 GiB). The bytes live on a volume under `jobhunter.files.dir` (`/data/files`, the `backend-files` claim in the
cluster) as `<first two hex>/<uuid>`; the display name is only a database column and is never used as a path. Metadata is in
`user_file` (changeset 011). All endpoints need a token and act on the caller's own files only; another user's id answers 404.

| Call | Meaning |
|---|---|
| `POST /api/files` (multipart `file`, optional `kind` = `CV` or `OTHER`) | store; 400 wrong CV content or unknown kind, 413 over the limit of the kind, 507 no room |
| `GET /api/files?kind=` | the caller's files, newest first (`id`, `name`, `kind`, `contentType`, `sizeBytes`, `uploadedAt`, `master`) |
| `GET /api/files/{id}` | details of one file |
| `POST /api/files/{id}/download-ticket` | `{"path": "/api/files/<id>/content?ticket=<hex>"}`: valid 60 s, one use, this file only |
| `GET /api/files/{id}/content` | the bytes as an attachment, with `Range` support; token **or** a ticket |
| `DELETE /api/files/{id}` | row first, then the bytes; 204 also for an unknown id |
| `PUT /api/files/master` `{"fileId"}`, `GET`, `DELETE /api/files/master` | choose, read, clear the master CV (kind `CV` only; uploads never change it) |

Why tickets: a browser cannot send a bearer token on a plain navigation, and fetching a multi-gigabyte file into a Blob would
exhaust the tab. The page asks for a ticket with its token, then points the browser at the returned path; the response is an
attachment, so the download streams to disk. Tickets live in memory (a restart drops them) and are never logged.

Uploads are streamed: Tomcat spools the part to `<dir>/tmp` (same volume) and the controller moves it into place with a rename
(`MultipartFile.transferTo(File)`; the `Path` overload would copy the bytes again). The multipart limits are 10 GB. Before the
body is read, a filter answers 507 when the declared size would leave less than `jobhunter.files.min-free-bytes` (2 GiB) free,
which keeps the database on the same disk alive. In the cluster an upload must also finish within the proxy's 60-second read
timeout (see `k8s/README.md`), which is the practical size limit for now. Settings: `jobhunter.files.dir`, `min-free-bytes`, `max-cv-bytes`,
`max-other-bytes`, `ticket-seconds`.

Old single CVs (the `cv_document` table of the first CV feature) are moved into the cabinet at start and set as master; a daily
sweep deletes stored files that no row refers to and that are older than 24 hours. There is **no backup** of the volume (same as
the database today), and the node's free disk is the only real limit. File bytes, names, tickets and CV text are never logged.

## CV extraction (cv module)

`POST /api/cv/extract` reads the caller's **master CV** (chosen in the cabinet) with the language model and returns a profile draft
(same shape as the profile's content); nothing is saved. 409 no master CV, 413 text over `jobhunter.cv.max-text-chars` (120000),
422 no text (a scanned PDF), 429 more than `jobhunter.cv.extractions-per-hour` (20) per user, 502 model failure, 503 no API key.
With `?section=experience|education|publications` (any letter case) the model is asked only for that section, every entry of it
(for experience every achievement, in the CV's wording), and the answer is a profile in which only that field is filled; an
unknown section answers 400 before anything else. A section request counts as one extraction for the hourly limit.
PDF text comes from Apache PDFBox (no OCR). CV text, prompts and model answers are never logged; log lines hold sizes, outcomes
and durations (`CvExtractApiTests` and `FilesLoggingTests` check this with markers).

## Language model (llm module)

`CvReader` turns CV text into a typed profile draft through Koog and Claude (`docs/koog-notes.md` explains the code).
Configuration: `ANTHROPIC_API_KEY` (read into `jobhunter.llm.api-key`, never logged; without it a call fails with a fixed
error) and `jobhunter.llm.model` (`Haiku_4_5`, `Sonnet_5` or `Opus_5`). `LetterWriter` (motivation letters) uses
`jobhunter.letters.model` (same names; empty = `jobhunter.llm.model`) and `jobhunter.letters.per-hour` (20, letter generations and regenerations per user and hour, 429 `rate_limited`; manual edits do not count; at most 20 versions per application, 409 `letter_limit`). Only the `llm` module imports `ai.koog`.

Logging: `logging.level.ai.koog` is `OFF` because Koog prints the model's raw answer when it cannot parse it. Do not turn
Koog or HTTP client DEBUG logging on in the cluster: prompts and answers hold CV text.
