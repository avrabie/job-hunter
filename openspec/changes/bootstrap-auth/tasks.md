# Tasks

All work is in `auth/`. Commands run from that folder (`cd auth && ./gradlew ...`). Tests need a container runtime for Testcontainers.

## 1. Build, configuration and local environment

- [ ] 1.1 Add test dependencies (`spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`) and a shared Testcontainers configuration using a pinned plain `postgres` image; verify `./gradlew test` starts a container and the generated context test connects
- [ ] 1.2 Set `server.servlet.context-path=/auth`, port 9090, `forward-headers-strategy=framework`, and the configuration properties (database, issuer, SPA redirect and post-logout URIs, UI base URL, optional Google credentials) with no real secrets; verify with a context test that the properties bind
- [ ] 1.3 Add `auth/compose.yaml` (Postgres with `authdb`) and `auth/README.md` (run, test, environment variables); verify `docker compose up -d` then `./gradlew bootRun` reaches the point where the missing changelog is the only failure, and fix the README after task 2.1

## 2. Schema and persistence

- [ ] 2.1 Write `db/changelog/db.changelog-master.yaml` and `001-users-roles.sql` (`users` with UUID id, unique index on `lower(email)`, provider columns and partial unique index on provider and provider id; `user_role` with cascade); verify with a test that the changelog applies to an empty database and a second start re-applies nothing
- [ ] 2.2 Write `002-password-reset.sql` (`password_reset_token` with hash, expiry, used time, cascade); verify the same test still passes and that deleting a user deletes its roles and tokens
- [ ] 2.3 Add Spring Data JDBC aggregates and repositories for users (with roles) and reset tokens, UUID assigned in code; verify with repository tests including a duplicate email differing only in case being rejected and a duplicate provider subject being rejected
- [ ] 2.4 Verify a deliberately broken changeset (test-only location) stops startup with an error naming the changeset

## 3. Authorization server core

- [ ] 3.1 Configure the authorization server filter chain with the fixed required issuer, the RSA key bean, the delegating password encoder and the `spa` public client (PKCE required, no consent, 15 minute access token, scopes `openid profile email`, redirect URIs from configuration); verify the discovery document at `/auth/.well-known/openid-configuration` has the configured issuer for any `Host` and `X-Forwarded-Host`, and the JWKS validates a token signature
- [ ] 3.2 Add tests for the SPA client: authorization code with PKCE succeeds, missing challenge is rejected, wrong verifier fails, unregistered redirect is rejected, client credentials and password grants are refused
- [ ] 3.3 Implement the token customizer (`roles` without prefix, `email`, `name` when scopes and values exist, no password data) and verify with decoded-token assertions for a user with and without a name
- [ ] 3.4 Verify `/api/me/**` rejects requests without a token with 401 and accepts a token issued by this server
- [ ] 3.5 Verify logout: the end-session endpoint with an ID token hint redirects to the registered post-logout URI and ends the session
- [ ] 3.6 Verify startup fails with a message naming the issuer setting when it is missing outside the test profile

## 4. Registration and form login

- [ ] 4.1 Implement `UserDetailsService` returning a `UserDetails` whose username is the user's UUID, found by email; verify with a test that login by email works and that the token `sub` is the UUID, not the email
- [ ] 4.2 Implement `POST /auth/api/register` (validation, email lowercased, role `USER`, bcrypt hash, 409 on duplicate, CSRF-exempt); verify with MockMvc tests: success 201, normalised email, duplicate 409, invalid input 400 with field names, password stored as a hash
- [ ] 4.3 Verify a concurrent registration test (two parallel requests, same email) leaves one row and one 409
- [ ] 4.4 Verify form login end to end during an authorization request (resumes and returns a code), wrong password and unknown email show the same generic error, and a Google-created account cannot be entered with a password

## 5. Password reset, change and mail

- [ ] 5.1 Implement the `MailSender` interface, the logging implementation (replaceable bean) and an after-commit helper; verify with tests that a message is delivered only after commit and not on rollback
- [ ] 5.2 Implement `POST /auth/api/password-reset/request` (always 200, send only for accounts with a local password, token from 32 random bytes, hash stored, 30 minute expiry); verify known, unknown and Google-only emails behave as specified and the database holds the hash, never the token
- [ ] 5.3 Implement `POST /auth/api/password-reset/confirm`; verify success (204, old password stops working), weak password (400, token still usable), expired, reused and unknown tokens give the same generic error
- [ ] 5.4 Implement `PUT /auth/api/me/password`; verify success, wrong current password, same password, Google-only account, and a test with two users proving user A's request cannot change user B's password
- [ ] 5.5 Compose the reset link from the UI base URL and send the "password changed" notice after change and reset; verify the logged messages contain the expected link and notice
- [ ] 5.6 Verify with a log-capture test that submitted passwords, tokens other than the logging sender's reset link, and `Authorization` headers never appear in logs

## 6. Google login

- [ ] 6.1 Register the Google client only when credentials are set and add the login-page link; verify the page shows the Google option with credentials and not without, and the server starts without them
- [ ] 6.2 Implement the OIDC user service with provisioning in a transaction: find by provider and subject (refresh names), else link by email and discard the local password, else create with role `USER`; unverified email is rejected; the principal name is the user's UUID; verify each branch with service tests, including concurrent first sign-ins creating one account
- [ ] 6.3 Port the authentication-factor authorities mapping for Spring Security 7 and verify a full Google login with a stubbed provider returns tokens whose `sub` equals the same user's UUID as a form login
- [ ] 6.4 Verify the squatter scenario: register `victim@example.com` with a password, sign in with Google for that email, and confirm the old password fails and password reset restores form login

## 7. Runtime and hand-off to the backend

- [ ] 7.1 Expose `/auth/actuator/health/liveness` and `/readiness` (readiness includes the DB, nothing else exposed) and verify: healthy 200 without a token, database down gives readiness 503 and liveness 200, other actuator endpoints 401 or 404
- [ ] 7.2 Verify refresh and userinfo with a UUID `sub` (code exchange, refresh token use, userinfo request); if any step assumes the principal name differs, apply the fallback customizer from the design and re-run
- [ ] 7.3 Document in `auth/README.md` the endpoints, claims, environment variables and how the backend should be configured (issuer URI, claims `roles`, `email`, `name`); verify by following the README from a clean checkout
- [ ] 7.4 When `backend` exists, run both locally, obtain a token from `auth` and call a `backend` `/api/` endpoint with it; verify the backend creates the user record keyed by the UUID `sub`

## Workflow follow-up

- Archive the change after groups 1 to 7 pass.
- Follow-up changes: email verification and SMTP sender, rate limiting and bot protection on registration and login, account deletion and export, persistent signing keys and authorization storage, user blocking and admin API.
