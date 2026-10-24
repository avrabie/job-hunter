# auth: code map

Spring Authorization Server for job-hunter: local and Google sign-in, registration, password reset, JWTs for the SPA.
Port 9090, context path `/auth`. Run instructions are in `README.md`; this file is the structure map.
Longer text: `docs/architecture-auth-gateway-ui.md`.

## Packages (`src/main/kotlin/net/s4v3/auth/`)

- `config/`: issuer and the single public client `spa` (PKCE, auth code + refresh), in-memory RSA signing key,
  three security filter chains (`SecurityConfig`), `AuthProperties` (`app.*`), `prompt=none` answer, logout fallback,
  dev-only seed accounts.
- `user/`: `User`, repository, registration (`POST /api/register`), form-login user lookup by email.
- `password/`: reset by email token (hash stored, 30 min), change password (`PUT /api/me/password`).
- `google/`: Google login, only when both credentials are set; links by verified email; `app.admin-emails` grants ADMIN.
- `token/`: adds `roles`, `email`, `name` claims; refresh tokens for the public client.
- `mail/`: `MailSender` interface; the only implementation logs the message.
- `web/`: JSON error mapping (`{error, fields}`).

## Stored where

PostgreSQL `authdb`: `users`, `user_role`, `password_reset_token` (Liquibase, `src/main/resources/db/changelog/`).
Memory only: signing key (new at each start, so a restart signs everyone out), the client, OAuth authorizations.

## Endpoints

Standard OIDC under `/auth` (authorize, token, JWKS, discovery, userinfo, logout); `POST /api/register`,
`/api/password-reset/{request,confirm}`; `PUT /api/me/password`; health at `/auth/actuator/health/{liveness,readiness}`.

## Run and test

`./gradlew bootRun --args='--spring.profiles.active=dev'` (Docker starts Postgres); `./gradlew test` (Testcontainers).
Tests: `src/test/kotlin/net/s4v3/auth/`, annotations `@IntegrationTest` and `@WebIntegrationTest`.

Read on 2026-10-24 from: auth/src/main/kotlin/**, auth/src/main/resources/**, auth/build.gradle.kts,
auth/src/test/kotlin/.../IntegrationTest.kt, k8s/README.md.
