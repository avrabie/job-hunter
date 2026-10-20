# auth

The job-hunter OAuth2/OIDC authorization server: Kotlin, Spring Boot 4, Spring Authorization Server,
Spring Data JDBC, Liquibase, PostgreSQL (`authdb`). It issues the JWTs that [`backend`](../backend) accepts, and
serves the login page. Requirements: `../openspec/`. Everything is mounted under **`/auth`** on port **9090**.

## Prerequisites

- JDK 25
- Docker (local database and the test suite)

## Run locally

```bash
cd auth
./gradlew bootRun --args='--spring.profiles.active=dev'
```

`spring-boot-docker-compose` (development only, not in the image) starts `compose.yaml` itself: PostgreSQL on
localhost:5433 with a local-only password, and the app connects to it without any setting. It also works from the
IDE: run `AuthApplication` with the working directory `auth/` and the active profile `dev`. Optional overrides
(issuer, Google credentials...) go in `.env` (see `.env.example`).

The profile is still needed because the issuer, UI and SPA URLs have no defaults (so production fails fast when they
are missing) and the sample accounts below exist only there.

Open the login page <http://localhost:9090/auth/login> and sign in with one of the seeded **dev-profile-only**
accounts (the login is the email):

| Email | Password | Roles |
|---|---|---|
| `user@example.com` | `user` | `USER` |
| `admin@example.com` | `admin` | `USER`, `ADMIN` |

They are created on startup when missing and exist in no other profile. Discovery is at
<http://localhost:9090/auth/.well-known/openid-configuration>. Note the issuer it reports is the **configured**
one (`ISSUER_URI`, default `http://localhost:8080/auth`, the address behind the gateway), whatever host you used.
Running without the gateway, set `ISSUER_URI=http://localhost:9090/auth` in `.env` so tokens and discovery agree.

Stopping the app stops the container too (data stays in the volume; `docker compose down -v` deletes it). PostgreSQL
reads its password only when it creates the volume, so after changing `AUTH_DB_PASSWORD` run `docker compose down -v`.

## Tests

```bash
./gradlew test      # needs Docker: tests start PostgreSQL containers
./gradlew build
```

## Settings

The issuer, UI base URL and SPA URIs have no defaults outside the `dev` profile: startup fails naming the missing
setting. The `dev` profile reads `.env` (see `.env.example`).

| Setting | Environment variable | Dev default |
|---|---|---|
| `app.issuer-uri` | `APP_ISSUER_URI` (dev: `ISSUER_URI`) | `http://localhost:8080/auth` |
| `app.ui-base-url` (reset links) | `APP_UI_BASE_URL` (dev: `UI_BASE_URL`) | `http://localhost:5173` |
| `app.spa.redirect-uris` (comma separated) | `APP_SPA_REDIRECT_URIS` (dev: `SPA_REDIRECT_URIS`) | `http://localhost:5173/callback` |
| `app.spa.post-logout-redirect-uris` | `APP_SPA_POST_LOGOUT_REDIRECT_URIS` (dev: `SPA_POST_LOGOUT_URIS`) | `http://localhost:5173/` |
| `app.google.client-id` / `client-secret` | `APP_GOOGLE_CLIENT_ID` / `APP_GOOGLE_CLIENT_SECRET` (dev: `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`) | unset: Google login is off |
| database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (dev: automatic from `compose.yaml`) | `localhost:5433/authdb`, user `authuser` |

Google login: create an OAuth client in the Google console and register
`<issuer>/login/oauth2/code/google` (for example `https://s4v3.net/auth/login/oauth2/code/google`) as its redirect URI.

## Endpoints

All paths are under `/auth`.

| Endpoint | Purpose |
|---|---|
| `GET /.well-known/openid-configuration`, `GET /oauth2/jwks` | discovery and signing keys (RSA, generated at startup) |
| `GET /oauth2/authorize`, `POST /oauth2/token`, `GET /userinfo`, `GET /connect/logout` | OAuth2/OIDC for the SPA client `spa` (public, authorization code + PKCE, refresh token, no consent) |
| `GET /login` | login page: email and password, plus "Sign in with Google" when configured |
| `POST /api/register` | `{"email","password","firstName"?,"lastName"?}` -> 201 `{"email"}`, 409 if taken, 400 `{"error","fields"}` |
| `POST /api/password-reset/request` | `{"email"}` -> always 200; a reset link is mailed (logged for now) |
| `POST /api/password-reset/confirm` | `{"token","newPassword"}` -> 204, or 400 `invalid_or_expired_token` |
| `PUT /api/me/password` | bearer token; `{"currentPassword","newPassword"}` -> 204; acts only on the token's `sub` |
| `GET /actuator/health/liveness`, `/readiness` | probes (readiness includes the database) |

Passwords are 8 to 72 characters. Until SMTP exists, mails are written to the log by the logging sender, including the
reset link with its token (a development stand-in).

## Tokens and the backend

- `iss`: the configured issuer. `sub`: the user's UUID (never the email), the same for form and Google login.
- `roles`: names without prefix (`["USER"]`, `ADMIN` is set by hand in the database).
- `email` and `name` when the `email` / `profile` scopes are granted and the values exist.
- Access tokens live 15 minutes, refresh tokens 60 minutes and are rotated on every use. Signing keys are generated
  at startup, so a restart invalidates all tokens.

Configure the backend with the same issuer:
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=<issuer>` (dev profile: `ISSUER_URI` in `backend/.env`). The
backend reads `sub`, `roles`, `email` and `name`.

## Layout

`config` (security, authorization server, properties), `user` (accounts, registration), `password` (reset and change),
`mail` (sender interface, logging sender), `token` (claims, refresh for the public client), `google`, `web`
(error mapping). Schema: `src/main/resources/db/changelog`.
