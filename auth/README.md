# auth

The job-hunter OAuth2/OIDC authorization server: Kotlin, Spring Boot 4, Spring Authorization Server,
Spring Data JDBC, Liquibase, PostgreSQL (`authdb`). It issues the JWTs the `backend` accepts.
Requirements: `../openspec/`. Mounted at `/auth`, port 9090.

## Prerequisites

- JDK 25
- Docker (local database and the test suite)

## Run locally

```bash
cd auth
cp .env.example .env            # then set AUTH_DB_PASSWORD in .env (git-ignored)
docker compose up -d            # PostgreSQL on localhost:5433
./gradlew bootRun --args='--spring.profiles.active=dev'
```

Stop with `Ctrl+C`; `docker compose down` stops the database (`-v` deletes its data). PostgreSQL only reads
`AUTH_DB_PASSWORD` when it first creates the volume; after changing it run `docker compose down -v`.

## Tests

```bash
./gradlew test      # needs Docker: tests start PostgreSQL containers
```

## Settings

| Setting | Environment variable | Dev default |
|---|---|---|
| `app.issuer-uri` | `APP_ISSUER_URI` (dev: `ISSUER_URI`) | `http://localhost:8080/auth` |
| `app.ui-base-url` | `APP_UI_BASE_URL` (dev: `UI_BASE_URL`) | `http://localhost:5173` |
| `app.spa.redirect-uris` | `APP_SPA_REDIRECT_URIS` (dev: `SPA_REDIRECT_URIS`) | `http://localhost:5173/callback` |
| `app.spa.post-logout-redirect-uris` | `APP_SPA_POST_LOGOUT_REDIRECT_URIS` (dev: `SPA_POST_LOGOUT_URIS`) | `http://localhost:5173/` |
| `app.google.client-id` / `client-secret` | `APP_GOOGLE_CLIENT_ID` / `APP_GOOGLE_CLIENT_SECRET` (dev: `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`) | unset: Google login is off |
| `spring.datasource.*` | `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` (dev: `AUTH_DB_PASSWORD`, `AUTH_DB_PORT`) | `localhost:5433/authdb` |

The issuer, UI base URL and SPA URIs have no defaults outside the `dev` profile: startup fails naming the
missing setting.
