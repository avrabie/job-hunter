# Proposal

## Why

The backend only accepts JWTs from an OAuth2 issuer, and the React UI needs a login with authorization code + PKCE. Nothing issues those tokens yet. This change builds the `auth` service (Spring Authorization Server, ported and adapted from Granite-Security/sample-shop's `auth-server`) so users can register, sign in with email and password or Google, and obtain tokens whose `sub` the backend can rely on as a stable user id.

## What Changes

- Implement the `auth` Spring Boot application (Kotlin, Spring Data JDBC, Liquibase, own `authdb` database) as an OAuth2/OIDC authorization server mounted at `/auth`, with a fixed issuer, a public SPA client using PKCE, and an RSA signing key generated at startup (all tokens become invalid after a restart; accepted).
- Issue access and ID tokens whose `sub` is an opaque, never-reused user id (UUID), plus `roles`, and `email` and `name` when the scopes allow, matching what the backend's `accounts` capability reads.
- Open registration with email and password through a JSON API, form login on the authorization server's login page, password reset by emailed one-time link, and password change for signed-in users.
- Google login that creates a local account automatically on first sign-in (verified Google email required) and links to an existing account by email only when that cannot hand the account to someone else.
- A mail sender interface with a logging implementation; SMTP comes in a later change. The link text carries the reset token, so the logging sender is a development stand-in.
- Health endpoints for probes, configuration from environment, and tests with Testcontainers.

**Explicitly out of scope** (later changes): Kafka or any event broker; email verification for local accounts and SMTP delivery; user blocking, admin user API, and account deletion or export; persistent signing keys and persistent authorization storage (so refresh tokens are not revoked on password reset and do not survive a restart); rate limiting and bot protection on registration and login; extra clients from sample-shop (gateway client, service-to-service clients); the login and registration screens in the React UI (the server provides the JSON API and Spring's generated login page); Dockerfile, CI and Kubernetes manifests (`bootstrap-k8s`).

## Capabilities

### New Capabilities
- `auth-tokens`: the authorization server itself: discovery and keys, the SPA client and PKCE, token lifetimes, and the exact claims in issued tokens.
- `auth-local-accounts`: registration, email and password login, password reset and change, and the mail sender abstraction.
- `auth-google-login`: sign-in with Google, automatic account creation, and safe linking to existing accounts.
- `auth-runtime`: the `authdb` schema, health endpoints, configuration from the environment, and local and test setup.

### Modified Capabilities
<!-- None. The backend's `accounts` capability already expects a `sub`, a `roles` claim and optional `email` and `name` claims; this change supplies them without changing those requirements. -->

## Impact

- **Modules touched:** none of the backend's Modulith modules. This is the separate `auth/` build. Interfaces with `backend` are the issuer URI, the JWKS, and the claims listed in `auth-tokens`. The `roles` claim carries role names without a `ROLE_` prefix (`USER`, `ADMIN`), because the backend adds the prefix when mapping.
- **New code:** `auth/src/main`, `auth/src/test`, Liquibase changelogs, `auth/compose.yaml` for a local Postgres, README.
- **Dependencies:** already in the scaffold (authorization server, OAuth2 client for Google, resource server, Spring Data JDBC, Liquibase, validation, actuator, PostgreSQL driver). To add: Testcontainers (`testcontainers-postgresql` and Spring Boot's Testcontainers support), and nothing else; Kafka and Lombok are deliberately not used.
- **Personal data:** the `authdb` stores email addresses, names and password hashes (bcrypt); Google logins store the Google subject id. Passwords and reset tokens never appear in logs except the reset link written by the temporary logging mail sender. Account deletion and export are not yet available (tracked as follow-up).
- **Per-user scoping:** `auth` holds only identity data. Its self-service endpoints act on the authenticated user alone, with a scenario proving one user cannot change another's password.
- **Claude cost:** none.
- **External systems:** Google OAuth (client id and secret supplied by environment). The backend and the React UI depend on this service's issuer URL, which `bootstrap-k8s` fixes as `https://s4v3.net/auth` in production.
