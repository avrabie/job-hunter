# Design

## Context

`auth/` is a Kotlin scaffold only (Boot 4.1.1, Java 25, Spring Authorization Server, OAuth2 client, resource server, Spring Data JDBC, Liquibase, validation, actuator, PostgreSQL driver). It is a standalone Gradle build with no shared code. The reference is Granite-Security/sample-shop's `auth-server`: Java, JPA, Liquibase, Kafka events, in-memory registered clients, a per-start RSA key, request-derived issuer, open `POST /api/register`, Google login that provisions or links accounts, password reset by emailed one-time token, and password change. Decisions already made: Kotlin, Spring Data JDBC, Liquibase, open registration, ephemeral signing key, mail logged until SMTP. See proposal.md for scope and the `accounts` and `platform-runtime` specs of `bootstrap-modulith-skeleton` for what the backend expects.

## Goals / Non-Goals

**Goals:**
- Port the working parts of sample-shop's server with the minimum of moving parts.
- Fix three problems of the original that matter once registration is open: mutable username as `sub`, Google linking that can hand an account to a squatter, and a request-derived issuer.
- Tokens that exactly match what `backend` reads.

**Non-Goals:**
- Features listed as out of scope in the proposal; a Modulith structure (this is a small single-purpose service); any new dependency beyond tests.

## Decisions

**Identity key.** Users have a UUID primary key. The email is the login identifier. The principal name, and so the token `sub`, is the UUID: for form login, `UserDetailsService.loadUserByUsername(email)` returns a `UserDetails` whose `username` is the UUID string (the DAO authentication provider names the authentication after the returned `UserDetails`, not the typed value); for Google login the `OidcUser` returned by our user service uses a claim holding the UUID as its name attribute, the same trick sample-shop uses with `preferred_username`. A test asserts `sub` is the UUID for both methods. Reason: with open registration and later account deletion, a username or email used as `sub` could be reused by another person and inherit the old person's data in `backend`. Alternative: keep username as `sub`. Rejected for that reason. Risk: Spring Authorization Server may assume the `sub` equals the principal name in places (userinfo, refresh); the tests for the refresh and userinfo paths cover it.

**No separate username.** Sample-shop asks for a username at registration. We drop it: email is the unique login, names are optional. Simpler form, fewer collisions, no username generation for Google users.

**Roles.** A `user_role` table (`user_id`, `role`), with `USER` granted on creation. Tokens carry the names without prefix; the authentication's authorities are `ROLE_USER` and so on, and the token customizer strips the prefix when writing `roles`. `ADMIN` is assigned by hand in the database for now. The customizer is the sample-shop one, reduced: it sets `roles`, `email` and `name`, and does not copy `picture`.

**Fixed issuer, context path.** `server.servlet.context-path=/auth`, `AuthorizationServerSettings.issuer(<configured>)`, required outside the test profile. The sample's request-derived issuer existed to serve two domains; we have one, and a fixed value cannot be corrupted by forwarded-header handling in the edge or the Spring gateway. `server.forward-headers-strategy=framework` is still set so redirects and the login page build correct external URLs behind the proxy. The registered SPA URIs come from configuration.

**Clients.** One `RegisteredClient` `spa` (public, `ClientAuthenticationMethod.NONE`, authorization code and refresh token, PKCE required, no consent, 15 minute access token, 60 minute refresh token default, scopes `openid profile email`). Built from configuration into an `InMemoryRegisteredClientRepository`. Alternative: JDBC-backed client repository. Not needed for one client.

**Signing key and authorization state.** An RSA 2048 key at startup and in-memory authorization storage, as in the sample. A restart logs everyone out; accepted. The `JWKSource` bean is the only place keys are created, so replacing it with a persistent source later is a one-bean change.

**Persistence.** Spring Data JDBC aggregates: `User` (id, email, password, firstName, lastName, enabled, provider, providerId, timestamps) with its roles as an aggregate collection, and `PasswordResetToken`. UUID ids are assigned in code, so entities implement `Persistable` (or use a `@Version`-less `isNew` flag) to make inserts explicit. Email uniqueness is a unique index on `lower(email)`; races are resolved by catching the unique violation and mapping it to a duplicate error, so concurrent registration cannot create two rows. Liquibase changelogs: `db/changelog/db.changelog-master.yaml` including SQL changesets (`001-users-roles.sql`, `002-password-reset.sql`), in the sample's SQL-with-comments style. Alternative: JPA like the sample. Rejected: the backend uses JDBC, and these aggregates are small.

**Registration and login.** `POST /auth/api/register` JSON (permit-all, CSRF-exempt like the sample because it has no session to ride on); bean-validation on the request record; password encoder is the delegating encoder (bcrypt), 8 to 72 characters because bcrypt truncates beyond 72 bytes. Form login uses Spring Security's generated page, extended by the Google link when configured. Duplicate registration returns 409 and thereby reveals that an email is registered; accepted for now and noted with rate limiting as follow-up.

**Google login.** Spring OAuth2 client registration `google` is created only when `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set, so local development and tests work without them. A user service wraps the default OIDC user service and calls the provisioning service (in a transaction): find by provider and subject → update names; else find by email → link and discard the local password (set to a random unusable hash) so a pre-registered squatter loses access; else create. Unverified Google email is rejected. Sample-shop's version keeps the local password when linking, which lets an attacker who registered the victim's email first keep access; the linking rule here removes that. The `FactorGrantedAuthority` mapping in the sample's `oauth2Login` is required by Spring Security 7's authentication-factor handling and is ported; a test covers a Google login end to end with a stubbed provider.

**Mail.** Interface `MailSender { send(Mail) }` with `LoggingMailSender` as the default bean (`@ConditionalOnMissingBean`) so an SMTP sender can replace it later. Messages are handed over through a transaction `afterCommit` hook, as in the sample, via a small `AfterCommit` helper. Reset link: `<ui-base-url>/reset-password?token=<token>`. The logged message contains the token, which is acceptable only because it is single-use, 30 minutes long, and the sender is temporary; this is called out in the risks.

**Reset tokens.** 32 random bytes from `SecureRandom`, URL-safe base64; SHA-256 hash stored; 30 minute expiry; `used_at` set on use; the confirm step looks up by hash. Same generic error for unknown, used and expired tokens.

**Endpoints and security chains.** Same layering as the sample, reduced: (1) authorization server endpoints, (2) `/api/me/**` as a resource server validating the server's own tokens with its in-process decoder, (3) the default chain with permit-all for `POST /api/register`, `/api/password-reset/**`, health, `/error`, form login and `oauth2Login`. The sample's internal user-admin chain and clients are dropped.

**Health.** Actuator on the same port: `/auth/actuator/health/liveness` and `/readiness` (readiness includes the DB), everything else on actuator not exposed. Matches the `workload-deployment` probes in `bootstrap-k8s`.

**Testing.** Testcontainers PostgreSQL (plain `postgres`, no pgvector needed) through Spring Boot's service connection; MockMvc for the JSON APIs; an end-to-end authorization code + PKCE test with a stubbed login; claim assertions on decoded tokens; Google flow with a mocked OIDC user service.

**New dependencies.** Only test-scoped: `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`. Everything else is in the scaffold. Kafka and Lombok are deliberately absent.

**Module boundaries and database schema.** No Modulith in `auth`; packages: `config`, `user`, `password`, `mail`, `token`, `google`. The Liquibase changelog above is the schema; no other service reads `authdb` (the init script in `bootstrap-k8s` gives it its own role).

## Risks / Trade-offs

- [`sub` as UUID may collide with framework assumptions] → Tests for code exchange, userinfo and refresh; fall back to a token customizer that sets `sub` if the principal-name approach fails.
- [Open registration invites abuse (bots, email enumeration, brute force)] → Accepted for the first release; follow-up for rate limiting at the gateway and email verification. Passwords are hashed with bcrypt and error messages are generic on login.
- [Logged reset links contain tokens] → Sender is temporary, tokens are single-use and short-lived; the logging sender is a development stand-in and the follow-up SMTP change removes it from production.
- [Ephemeral key: restarts log everyone out and invalidate refresh tokens] → Accepted; replacing `JWKSource` later is local.
- [Account takeover by pre-registration] → The Google linking rule discards the squatter's password; local accounts remain unverified until the email verification follow-up.
- [Spring Security 7 factor authorities] → Port the sample's mapper and test the Google flow.
- [`/auth/actuator/health` reachable through the public gateway route] → It exposes status only; the gateway can block it later.

## Migration Plan

New service, no data to migrate. The database and role are created by the cluster init script (`bootstrap-k8s`); locally by `auth/compose.yaml`. Rollback is removing the deployment.

## Open Questions

- Sender address and subject texts for the password mails (placeholder strings now; needed when SMTP is added).
- Whether the UI will want a username/display name beyond first and last name (affects the `name` claim only).
