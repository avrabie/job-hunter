# Job Hunter: Plan

Crawl software-engineering job postings in Switzerland, score them against each user's profile, and use Claude to draft a tailored CV and motivation letter (EN/DE) for each good match. A human reviews everything; nothing is sent automatically.

Users: open registration (email/password or Google), each with their own account and data. I pay for Claude usage by default; users may optionally bring their own Claude API key.

## 1. Stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.x, JVM 21+ |
| Framework | Spring Boot 4 (Spring Framework 7), servlet (MVC) + JDBC on virtual threads |
| Structure | Spring Modulith (modular monolith) |
| Build | Gradle, Kotlin DSL |
| DB | PostgreSQL with pgvector (Liquibase migrations) |
| UI | React + TypeScript (Vite), OIDC login (`oidc-client-ts`, authorization code + PKCE), OpenAPI-generated client. **Built:** sign-in, profile page, postings list and detail, admin page for the crawl schedule |
| Auth | Own Spring Authorization Server (`auth` Gradle module in this repo, ported and adapted from the one in Granite-Security/sample-shop); the backend is an OAuth2 resource server validating its JWTs |
| Gateway | Spring Cloud Gateway (WebFlux, Kotlin) in `gateway/`: stateless path router for `/api` and `/auth`; holds no session or token (modelled on the gateway in Granite-Security/sample-shop) |
| Repo layout | One repo, three independent Gradle builds, each with its own wrapper: `backend/` (the modulith), `auth/` and `gateway/`, plus the `ui/` build (pnpm, Vite); built, tested and deployed separately, no shared code. `k8s/` holds the cluster setup instructions and manifests (Gateway API resources, Kustomize base and overlays) |
| HTTP | Ktor client or Spring `RestClient`; Playwright only where JS rendering is unavoidable |
| LLM | Koog (JetBrains Kotlin agent framework) with Claude as the provider; confined to the `llm` module (section 7) |
| Deploy | Own one-node Kubernetes cluster, Kustomize overlays, Kubernetes Gateway API (HTTPRoutes; implementation such as Traefik) + cert-manager |
| Observability | Micrometer + Prometheus/Grafana (later), structured logs |

MVC + JDBC rather than WebFlux + R2DBC: the Modulith event publication registry documents JPA/JDBC/MongoDB/Neo4j backends but not R2DBC (to re-check for Modulith 2.x), and the Anthropic SDK and crawling libraries are blocking. Exact versions are pinned from start.spring.io / Maven Central at scaffold time.

## 2. Architecture

```
Browser (React SPA, oidc-client-ts, PKCE)
   |  Bearer JWT
   v
Kubernetes Gateway API (Gateway + HTTPRoutes, TLS via cert-manager, by hostname)
   |-- /          --> ui            (React static, nginx)
   |-- /auth/**   --> gateway  --> auth       (Spring Authorization Server, own DB; issues JWT)
   |-- /api/**    --> gateway  --> jobhunter  (Spring Boot modulith, resource server, 1 replica)

jobhunter --> PostgreSQL + pgvector (StatefulSet, PVC)
          --> Claude API, job sites (egress only)
```

- Workloads: `gateway`, `auth` (+ its Postgres DB), `jobhunter`, `ui`, `postgres`. No Kafka (single backend service; Modulith events are enough).
- Two different "gateways": the cluster's Gateway API resource is the edge (TLS, hostnames, sends `/` to `ui` and `/api`, `/auth` to the Spring gateway); the Spring Cloud Gateway app is a stateless router behind it.
- The Spring gateway routes by path only, plus security headers, retries for idempotent requests and connection-pool settings. It is not an OAuth2 client and holds no token or session: the SPA runs authorization code + PKCE itself and sends its own Bearer token. Adding a route there grants access; it does not restrict it.
- Each service still validates the JWT itself (issuer URI + JWKS); the gateway never authorizes.
- Lessons carried over from sample-shop's gateway: bound the connection pool (idle and life time) so deploys do not cause 500s from stale connections, and let `X-Forwarded-*` reach `auth` untouched so its issuer is not derived from an internal URI (we also set a fixed issuer).
- Scheduling runs inside `jobhunter` (db-scheduler/ShedLock, one replica), so no unauthenticated trigger endpoints are needed.
- The auth service issuer URL must be a stable public HTTPS URL, since it is the JWT `iss`.

## 3. Pipeline

```
Sources -> Fetch -> Normalize -> Dedupe -> Store (shared)
   -> per user: pgvector prefilter -> Claude score (top candidates only) -> Generate (CV + letter) -> Human review
```

## 4. Modules (Spring Modulith)

| Module | Responsibility |
|---|---|
| `sources` | Source adapters (one per site/API), fetching, rate limiting, robots.txt. **Built:** Greenhouse board adapter (`AtsCrawl`) with Swiss and IT filters, robots.txt, conditional fetch, opt-in startup run. Hourly scheduler with an ADMIN-only API to change the schedule at runtime (stored in the database). No other ATS yet |
| `postings` | Normalized `JobPosting` model, dedupe, persistence, raw payload storage. Shared across users. **Built:** shared storage, idempotent ingest (`PostingIngest`) with cross-source dedupe, language detection (EN/DE/FR/IT), `JobIngested` event, read API (`PostingQuery`). No source adapters or scheduling yet |
| `accounts` | JWT resource-server config, current-user context (`sub`), roles, user records, account deletion/export |
| `profile` | Per-user master profile (experience, skills, preferences). Single source of facts for that user. **Built:** one JSON document per user, `GET`/`PUT /api/profile` with a version check, server validation, and the UI page at `/profile` with live field checks. CV import is not built yet |
| `matching` | Per-user prefilter + Claude scoring of postings against the profile; structured JSON output. **Built:** the deterministic prefilter (hard rules and a 0-100 score), stored per user and posting, evaluated on `JobIngested` and `ProfileSaved`, and `GET /api/matches`. No Claude scoring or embeddings yet |
| `applications` | Per-user CV and motivation-letter generation, fact validation, PDF/DOCX export |
| `llm` | Koog-based LLM services (scoring, drafting), prompt templates, caching, per-user usage and quotas, BYO key handling |
| `web` | REST API (OpenAPI) for the React UI. **Built:** generated OpenAPI document at `/api/openapi.json` and a Swagger UI page at `/api/docs`, with a guard test that every endpoint is documented; the profile, postings (`/api/postings`), matches and admin schedule endpoints |
| `notifications` | Daily digest (email) |

Modules communicate through events (`JobIngested`, `JobScored`, `ApplicationDrafted`) using the Modulith event publication registry. `ApplicationModules.verify()` runs in the test suite.

## 5. Multi-user rules

- **Shared data:** postings and their embeddings (crawled once for everybody).
- **Per-user data:** profile, CV, preferences, scores, drafts, application status, API key, usage. Every query is scoped by the user id from the JWT `sub`; this rule is written into each module's spec and covered by tests (user A can never read user B's data).
- **Open registration:** anyone can register with email and password or sign in with Google, which creates a local account automatically. Because I pay for Claude by default, new accounts get a small default quota (or must add their own key) so open sign-up cannot run up my bill; numbers are an open question.
- **Quotas:** per-user limits on scoring and generation calls (and token budget) when using my key; rate limiting on the API. Users on their own key are not limited by my budget.
- **Bring your own Claude key (optional):**
  - User can add/remove an Anthropic API key in settings. Keys are write-only in the UI (never displayed back).
  - Encrypted at rest (AES-GCM; encryption key from a Kubernetes Secret), decrypted only in memory when making a call, never logged.
  - The `llm` module selects the user's key if present, otherwise my key with quota.
  - Usage and cost are tracked per user either way.
- **Privacy (nDSG / GDPR):** CVs are personal data. Privacy notice and explicit consent that CV data is sent to Anthropic, account deletion (hard delete of all user data), data export, no CV content in logs.

## 6. Market: Switzerland

- Postings are mostly in German, English, or French. Detect language per posting and store it; French/Italian postings are out of scope at first (flagged, not drafted).
- Locations normalized to canton/city; salary in CHF when present; workload (percentage, e.g. 80-100%) and remote/hybrid captured.
- Profile includes work-permit / nationality status, since it is a hard filter for many Swiss postings.

### Sources (priority order; each to be verified for API availability and ToS before building)

1. Swiss boards: jobs.ch, SwissDevJobs, jobup.ch, Job-Room (job-room.ch, official), ostjob, Jobscout24.
2. Public ATS endpoints of Swiss companies and tech employers: Greenhouse, Lever, Ashby, Workable, SmartRecruiters, Personio (crawl by company list).
3. Aggregator APIs/feeds: Adzuna, Arbeitnow, RemoteOK, We Work Remotely, HN "Who is hiring" (filtered to Switzerland/remote-EU).
4. LinkedIn/Indeed: not scraped (ToS). Use their email alerts as input if wanted.

Rules: respect robots.txt, per-host rate limits, identifiable User-Agent, idempotent crawls, raw payload stored for re-parsing.

## 7. AI design principles

- The user's master profile is the only source of facts. Claude selects and rewrites; it must not invent skills, employers, or dates.
- A validation pass compares the generated CV/letter against the profile and flags anything unsupported.
- Cheap model (Haiku) for scoring, stronger model (Sonnet) for writing. Prompt caching on the profile.
- Structured output (JSON schema) for scoring.
- Every draft is stored with the posting, prompt version, and model used.

### Languages and documents

| Document | EN | DE |
|---|---|---|
| CV | yes | yes |
| Motivation letter | yes | yes |

- The output language follows the posting language by default, overridable in the UI. Both versions can be generated for the same posting.
- German output targets Swiss business German conventions (Hochdeutsch, no ß, formal "Sie", Swiss CV layout expectations such as personal details block and optional photo).
- **Inputs from each user:** their CV and optionally a sample motivation letter. The CV builds the initial master profile (extracted, then reviewed and corrected by the user); the sample letter defines tone, structure, and length (style reference, never copied verbatim). My own CV and sample letter are used for development and go in a git-ignored `private/` folder; they are never committed.
- The master profile holds facts once, language-neutral; the DE version is written from the profile, not machine-translated from the EN output, then checked by the same fact validation.
- "Recommendation letters" is read here as motivation/cover letters. If reference letters (Arbeitszeugnis-style) are meant, that is a different feature.

### Koog decision

[Koog](https://docs.koog.ai) is JetBrains' Kotlin agent framework (1.0 stable as of May 2026; Anthropic support incl. prompt caching; Spring Boot starter).

- **Decision: use Koog as the LLM framework from the start** (not a raw provider SDK), so we are not tied to one client. Claude is the provider; others can be swapped in later through Koog's prompt executors.
- Used for: structured output (scoring), the draft -> critique -> validate -> revise graph (generation), tracing/OpenTelemetry, and prompt caching of the user profile.
- Containment: only the `llm` module imports Koog. Other modules call it through the module's public API (e.g. `ScoringService`, `DocumentDrafter`), so Koog stays replaceable and the per-user key/quota logic stays in one place. A per-request executor is built with the user's own key when they bring one.
- Risks to verify in the first LLM change: Koog's Spring Boot integration is marked beta and Spring Boot 4 compatibility is not documented, so we may wire Koog manually (plain Kotlin, no starter). Keep modules loosely coupled to it either way.

## 8. Deployment (own one-node cluster)

- Kustomize overlays (as in my other project); images built by CI and pushed to a registry (to decide).
- Gateway API implementation (e.g. Traefik) + cert-manager for TLS. HTTPRoutes: `/` to `ui`, `/api` and `/auth` to the Spring gateway.
- Postgres as a StatefulSet with the pgvector image (CloudNativePG optional later). One Postgres instance, separate databases for `authdb` and `jobhunter`.
- **Backups:** nightly `pg_dump` CronJob to off-node storage. A single node with local disks is the weak point.
- Resource requests/limits on every workload (Playwright, if used, runs as a separate bounded workload).
- Secrets: Anthropic key, BYO-key encryption key, DB passwords, auth service client secrets.

## 9. Phases

0. **OpenSpec setup** (before any code): install the OpenSpec CLI (`npm install -g @fission-ai/openspec@latest` or `brew install openspec`; needs Node 20.19+), run `openspec init` in `job-hunter/` (select Claude Code), fill `openspec/config.yaml` project context, then deliver the Spring Boot skeleton itself as the first OpenSpec change (`bootstrap-modulith-skeleton`, via propose -> apply -> archive). Write the first specs, one per capability/module, including module events as contracts and the per-user scoping rule. `PLAN.md` stays the high-level roadmap; detailed requirements live in the specs. Every phase below is delivered as one or more OpenSpec changes.
1. **Skeleton and platform** (`bootstrap-modulith-skeleton`, then `bootstrap-auth`, then `bootstrap-gateway`, plus follow-ups; the repo holds three independent Gradle builds, `backend/`, `auth/` and `gateway/`): Gradle project, Modulith modules, Postgres + Liquibase, resource-server security against our own auth service, local dev setup, Kustomize base.
2. **Ingestion**: 2-3 ATS/API sources, scheduled ingestion, dedupe, posting language detection.
3. **Matching**: per-user profile import from CV (human-reviewed), `llm` module on Koog (Claude provider), embeddings + pgvector prefilter, Haiku scoring, quotas and usage tracking.
4. **Generation**: CV + motivation letter drafting (EN/DE), fact validation, PDF/DOCX export; Koog draft/critique/validate graph; BYO key.
5. **UI and ops**: React app with OIDC login, review/edit flow, status tracking feeding back into scoring, daily digest, backups, metrics, account deletion/export.

pgvector is used from phase 3 for semantic matching (posting embeddings vs. profile), alongside the LLM score.

## 10. Decided

- Market: Switzerland. UI: React. DB: Postgres + pgvector.
- Documents: CV and motivation letters in EN and DE.
- Multi-user, open registration; I pay for Claude by default; optional bring-your-own key.
- Auth: our own Spring Authorization Server as a second Gradle module in this repo, ported from sample-shop's `auth-server` and adapted (no Kafka, direct mail that is logged at first and sent via SMTP later); backend is a resource server.
- Own one-node cluster; modular monolith, MVC + JDBC; no Kafka.
- Edge: Kubernetes Gateway API (not Ingress) with a separate Spring Cloud Gateway behind it for `/api` and `/auth`.

## 11. Open questions

- **Auth port:** sample-shop's `auth-server` is Java + JPA + Liquibase + Kafka. Decided: open registration, Liquibase for both modules, RSA signing key generated at startup (all tokens invalid after a restart is accepted), mail logged only at first with SMTP later. Still to decide: language (Kotlin vs keep Java), persistence (JPA vs JDBC), and which clients and endpoints to keep.
- Spring Cloud Gateway version compatible with Spring Boot 4.1 (verify in `bootstrap-gateway`).
- Kubernetes distro, Gateway API implementation (Traefik assumed), default StorageClass, container registry.
- Embedding model: Anthropic has no embeddings API, so choose one (e.g. Voyage AI, or a local model) before phase 3.
- Quota numbers per user (calls/tokens per month) for my key.
- Seniority, stack, and canton/remote preferences defaults for scoring.
- Photo on the CV: yes/no, and CV layout template preference.

## 12. To do later

- **Registration abuse protection.** `POST /auth/api/register` is public and open: no rate limit per address, no CAPTCHA or other bot check, no email verification, so anyone can create accounts with any address. Before real users arrive, decide and build: rate limiting at the gateway or edge (`X-Forwarded-For` already reaches the services), a bot check on the registration form, and email verification once SMTP exists in `auth`. Needs its own OpenSpec change.
