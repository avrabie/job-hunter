# job-hunter

A job-search assistant for the Swiss IT market. It crawls public job boards (Greenhouse for now), stores the postings,
lets each user keep a profile and a master CV, and uses Claude (through Koog) to read a CV into a profile draft.
It is a Kotlin / Spring Boot 4 backend with its own OAuth2 server, a gateway and a React UI.

## Architecture

```
 browser --> ui (Vite dev server, :5173)
                |  proxies /api and /auth
                v
            gateway (:8080) ---- /api/** ---> backend (:8081) ---> PostgreSQL + pgvector (:5432, db jobhunter)
                |                                  ^
                +------------ /auth/** --> auth (:9090, context path /auth) ---> PostgreSQL (:5433, db authdb)
                                       (backend fetches auth's signing keys to verify tokens)
```

| Part | Directory | Port | What it does |
|---|---|---|---|
| ui | `ui/` | 5173 | React + TypeScript on Vite; signs in with authorization code + PKCE |
| gateway | `gateway/` | 8080 | Stateless Spring Cloud Gateway: `/api/**` to backend, `/auth/**` to auth |
| backend | `backend/` | 8081 | Modular monolith (postings, profile, files, CV extraction, crawl schedule); OAuth2 resource server |
| auth | `auth/` | 9090 | OAuth2/OIDC authorization server and login page, under `/auth` |
| databases | compose files | 5432, 5433 | Two PostgreSQL containers, started automatically by `bootRun` |

## Prerequisites

| Tool | Version | Source |
|---|---|---|
| JDK | 25 | toolchain in each `build.gradle.kts` |
| Gradle | 9.8.1 through the `./gradlew` wrapper in backend, auth, gateway (nothing to install) | `gradle-wrapper.properties` |
| Docker | recent Docker Desktop / Engine with Compose | databases and Testcontainers tests |
| Node | 22 or newer | `ui/package.json` `engines` |
| pnpm | 12.14.0, pinned (only `pnpm-lock.yaml` exists; do not use npm or yarn) | `ui/package.json` `packageManager` |

Install pnpm with `npm i -g pnpm@12.14.0` (or `corepack enable` on a Node that bundles Corepack).

## Configuration

Nothing is required to start the stack: the `dev` profile has local-only defaults. Everything below is optional, except
the Anthropic key for CV extraction.

**Git-ignored (never commit secrets):** `.env` (in any directory, root `.gitignore`), `private/` (any directory with that
name; keep real CVs and letters there), `**/secrets.yaml` (`k8s/.gitignore`), `build/`, `.gradle/`, `node_modules`.

| Variable | Used by | Purpose | Default |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | backend | Claude API key, read into `jobhunter.llm.api-key` (`backend/src/main/resources/application.yaml`) | empty: CV extraction answers 503 |
| `jobhunter.llm.model` (env `JOBHUNTER_LLM_MODEL`) | backend | model constant: `Haiku_4_5`, `Sonnet_5` or `Opus_5` | `Haiku_4_5` |
| `DB_PASSWORD`, `DB_PORT` | backend compose | database password and host port | `jobhunterdev`, `5432` (db `jobhunter`, user `jobhunter`) |
| `AUTH_DB_PASSWORD`, `AUTH_DB_PORT` | auth compose | auth database | `authdev`, `5433` (db `authdb`, user `authuser`) |
| `ISSUER_URI` | backend, auth (dev) | public token issuer; must be identical in both | `http://localhost:8080/auth` |
| `UI_BASE_URL`, `SPA_REDIRECT_URIS`, `SPA_POST_LOGOUT_URIS` | auth (dev) | UI addresses allowed for sign-in | `http://localhost:5173`, `.../callback`, `.../` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | auth (dev) | enables "Sign in with Google" (outside dev: `APP_GOOGLE_CLIENT_ID`, `APP_GOOGLE_CLIENT_SECRET`) | unset: Google login off |
| `APP_ADMIN_EMAILS` | auth | comma-separated emails that get role `ADMIN` when they sign in with Google | empty |
| `JOBHUNTER_SOURCES_SCHEDULE_ENABLED` | backend | `true` turns the hourly crawl on (an admin can also change it at `/admin`) | `false` |
| `JOBHUNTER_SOURCES_GREENHOUSE_RUN_ON_STARTUP` | backend | `true` runs one crawl after startup | `false` |
| `JOBHUNTER_FILES_DIR` | backend | where uploaded files are stored | `/data/files` (see the troubleshooting note) |
| `GATEWAY_URL` | ui dev server | where `/api` and `/auth` are proxied | `http://localhost:8080` |

JWT signing: there is no key setting. auth generates an RSA key at every start, so a restart invalidates all tokens. The
issuer is `ISSUER_URI`.

Copy-pasteable setup (placeholders only):

```bash
# optional overrides, read by the dev profile (both files are git-ignored once copied)
cp backend/.env.example backend/.env
cp auth/.env.example auth/.env
# in auth/.env, for Google login:  GOOGLE_CLIENT_ID=<your-client-id>   GOOGLE_CLIENT_SECRET=<your-client-secret>
# Google redirect URI to register:  http://localhost:8080/auth/login/oauth2/code/google

# export in the terminal that starts the backend; do not write it into a tracked file
export ANTHROPIC_API_KEY=<your-anthropic-api-key>
# or, equivalently, put the line  ANTHROPIC_API_KEY=<your-anthropic-api-key>  into backend/.env (the dev profile imports that file
# and application.yaml reads ${ANTHROPIC_API_KEY:}); backend/.env is git-ignored
# in the terminal that starts auth, optional
export APP_ADMIN_EMAILS=<you@example.com>
```

## Run everything locally

Each step in its own terminal, from the repository root. Docker must be running: the databases start by themselves.
Sign-in goes through the UI dev server, so auth and backend are started with the issuer `http://localhost:5173/auth`
(as `ui/README.md` prescribes). Start in this order:

| # | Directory | Command | Health check |
|---|---|---|---|
| 1 | `auth/` | `ISSUER_URI=http://localhost:5173/auth ./gradlew bootRun --args='--spring.profiles.active=dev'` | `curl localhost:9090/auth/actuator/health/readiness` |
| 2 | `backend/` | `ISSUER_URI=http://localhost:5173/auth ./gradlew bootRun --args='--spring.profiles.active=dev'` | `curl localhost:8081/actuator/health/readiness` |
| 3 | `gateway/` | `./gradlew bootRun` | `curl localhost:8080/actuator/health/readiness` |
| 4 | `ui/` | `pnpm install` once, then `pnpm dev` | open http://localhost:5173 |

Step 2 needs `ANTHROPIC_API_KEY` exported for CV features. Each `bootRun` starts its own PostgreSQL through
`spring-boot-docker-compose`; stopping the app stops the container and the data stays in the volume
(`docker compose down -v` in that directory wipes it; do that after changing a database password).

**Sign in.** Open http://localhost:5173, choose Register, create an account (password 8 to 72 characters) and sign in.
The dev profile also seeds `user@example.com` / `user` and `admin@example.com` / `admin` (role `ADMIN`, needed for the
`/admin` button); they exist only in the `dev` profile. "Sign in with Google" appears only when the Google variables are
set. Local-password accounts never get `ADMIN` from `APP_ADMIN_EMAILS`.

**Without the UI** (gateway only): start auth and backend without `ISSUER_URI` (default `http://localhost:8080/auth`) and
open http://localhost:8080/auth/login. API docs: http://localhost:8081/api/docs (OpenAPI at `/api/openapi.json`).

## Tests and builds

| Part | Directory | Command | Notes |
|---|---|---|---|
| backend | `backend/` | `./gradlew build` (fast tier), `./gradlew verify` (everything) | see the backend commands below; `verify` needs Docker |
| auth | `auth/` | `./gradlew test`, `./gradlew build` | needs Docker |
| gateway | `gateway/` | `./gradlew test`, `./gradlew build` | no Docker, no network |
| ui | `ui/` | `pnpm lint && pnpm typecheck && pnpm test && pnpm build` | output in `ui/dist/` |

### Backend commands

The backend tests are split in two tiers. The profile you may remember is Spring's `dev` profile for **running** the app; tests
activate their own `test` profile and need no flag.

| What | Command (in `backend/`) | Needs Docker | About |
|---|---|---|---|
| Fast tier: unit tests (no Spring context) | `./gradlew build` or `./gradlew test` | no | 25 s from clean, 294 tests |
| Integration tier: Spring + Postgres container | `./gradlew integrationTest` | yes | 50 s, 280 tests |
| Everything (run before every deploy) | `./gradlew verify` | yes | 75 s, 574 tests |
| One class | `./gradlew test --tests "ProfileValidatorTests"` (use `integrationTest` for integration classes) | depends | seconds |
| Clean everything first | add `clean`, for example `./gradlew clean verify` | | |
| Run the app locally | `ISSUER_URI=http://localhost:5173/auth ./gradlew bootRun --args='--spring.profiles.active=dev'` | yes (compose) | see "Run everything locally" |

Integration tests are the classes tagged `integration` (the `@IntegrationTest` and `@ApiTest` annotations carry the tag). They share
one Postgres container for the whole run.

## Troubleshooting

- **CV extraction or analysis answers 503:** `ANTHROPIC_API_KEY` is not set in the backend's environment. Export it and restart.
- **409 on "Fill from CV":** no master CV. Upload a CV (PDF, `.md`, `.txt`, max 5 MB) on `/profile` and press "Use as master CV".
- **422, 413, 429 on extraction:** scanned PDF without text (no OCR), text too long, hourly limit per user.
- **Uploads fail locally:** the files directory defaults to `/data/files`; try `JOBHUNTER_FILES_DIR=<a writable folder>` for the backend.
- **A database password was changed and the app cannot connect:** run `docker compose down -v` in that directory.
- **Sign-in loops or 401 after login:** the issuer differs between auth, backend and the UI. Restart auth and backend with the same `ISSUER_URI`. A plain 401 from `/api/...` without a token is expected.
- **Tokens stop working after restarting auth:** signing keys are regenerated; sign in again.
- **Docker Hub credential helper** (`'username' must not be null`) when building images with the Paketo buildpack on Docker Desktop for Mac: pull the builder and run image with `docker pull` first, see `k8s/README.md` section 5. Not needed for `bootRun` or tests.

**Works without an Anthropic key:** registration and sign-in, the postings crawl and list, search and filters, the profile
form and saving, the file cabinet (upload, download, master CV), repository listing, the admin crawl schedule.
**Needs the key:** the "Fill the profile from my CV" buttons (whole profile and the Experience, Education and Publications
sections); these also need a master CV.

## More documentation

- [`backend/README.md`](backend/README.md), [`auth/README.md`](auth/README.md), [`gateway/README.md`](gateway/README.md), [`ui/README.md`](ui/README.md): per-service details, endpoints, settings.
- [`PLAN.md`](PLAN.md): roadmap and architecture decisions.
- [`openspec/`](openspec): requirements (`specs/`) and changes (`changes/`), managed with the OpenSpec workflow (`/opsx:propose`, `/opsx:apply`, `/opsx:archive` in Claude Code).
- [`k8s/README.md`](k8s/README.md): production deployment on https://s4v3.net (Kubernetes, image builds, secrets from `k8s/*.yaml.example`); `k8s/overlays/local` is a kind-cluster variant on http://localhost:8080.
- [`docs/koog-notes.md`](docs/koog-notes.md): notes on the Koog LLM library.
- [`docs/architecture.md`](docs/architecture.md) (backend), [`docs/architecture-auth-gateway-ui.md`](docs/architecture-auth-gateway-ui.md), [`docs/glossary.md`](docs/glossary.md): how the code fits together; each backend module also has a short `README.md` in its package.
- [`docs/deprecations.md`](docs/deprecations.md), [`docs/simplification-candidates.md`](docs/simplification-candidates.md), [`docs/cv-export.md`](docs/cv-export.md): maintenance notes.
