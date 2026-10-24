# accounts

Purpose: who is calling. The backend is an OAuth2 resource server; this module validates tokens, maps roles, gives other modules the caller's id and keeps the user record.

## Public API (this package)
- `CurrentUser.id()`: the token `sub`. The only way other modules learn the caller.
- `NoCurrentUserException`: no authenticated user in the context.
- `UserRegistered(userId, registeredAt)`: event.

## Internal (`internal/`)
- `SecurityConfig`: filter chain (stateless, CSRF off), public paths, `/api/admin/**` needs `ROLE_ADMIN`, roles claim to `ROLE_*`.
- `UserProvisioningFilter` and `UserProvisioner`: after token validation, insert the user if new.
- `JwtCurrentUser`: implements `CurrentUser`.
- `IssuerSettingCheck`: fails startup without the issuer URI (profile `test` exempt).

## Events
- Publishes `UserRegistered` once per new `sub`. No consumer yet. Consumes none.

## Tables
- `app_user` (`db/changelog/001-baseline.sql`).

## Settings
- `spring.security.oauth2.resourceserver.jwt.issuer-uri`, `app.security.roles-claim` (default `roles`), `jobhunter.api-docs.enabled` (public docs paths).

## Calls
- No other module.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `ResourceServerTests`, `UserProvisioningTests`, `ConcurrentProvisioningTests`, `UserRegisteredEventTests`, `CurrentUserTests`, `IssuerSettingTests`.
- ArchUnit: `TokenAccessBoundaryTests` (only this module reads the token).

Read on 2026-10-24 from: accounts/*.kt, accounts/internal/*.kt, db/changelog/001-baseline.sql, test file names.
