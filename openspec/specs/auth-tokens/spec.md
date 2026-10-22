# auth-tokens Specification

## Purpose
Defines the OAuth2/OIDC authorization server: how clients discover it and obtain tokens, and exactly what the issued tokens contain, so that the backend and the SPA can rely on them.

## Requirements

### Requirement: Fixed public issuer
The server SHALL advertise one fixed issuer URL taken from configuration, independent of the request's host or forwarded headers, and SHALL serve all its endpoints under the `/auth` path.

#### Scenario: Discovery document
- **WHEN** a client requests `/auth/.well-known/openid-configuration` with any `Host` or `X-Forwarded-Host` value
- **THEN** the `issuer` in the response equals the configured issuer and every endpoint URL starts with it

#### Scenario: Token issuer claim
- **WHEN** a token is issued
- **THEN** its `iss` equals the configured issuer

### Requirement: Signing keys
The server SHALL sign tokens with an RSA key generated at startup and publish the public key at its JWKS endpoint.

#### Scenario: Backend validation
- **WHEN** a token issued by the server is validated against its JWKS
- **THEN** the signature verifies and `iss` matches

#### Scenario: Restart
- **WHEN** the server restarts
- **THEN** it publishes a new key, and tokens issued before the restart fail validation

### Requirement: Public SPA client with PKCE
The server SHALL register one public client for the SPA, without a client secret, that supports only the authorization code and refresh token grants, requires PKCE, and accepts only the configured redirect and post-logout URIs.

#### Scenario: Code flow with PKCE
- **WHEN** the SPA starts an authorization request with a `code_challenge`, the user signs in, and the SPA exchanges the code with the matching `code_verifier`
- **THEN** the server returns an access token, an ID token and a refresh token

#### Scenario: Missing PKCE
- **WHEN** an authorization request for the SPA client has no `code_challenge`
- **THEN** the server rejects it

#### Scenario: Wrong verifier
- **WHEN** the code is exchanged with a `code_verifier` that does not match
- **THEN** the token request fails

#### Scenario: Unregistered redirect
- **WHEN** an authorization request uses a redirect URI that is not registered for the client
- **THEN** the server rejects it and does not redirect to that URI

#### Scenario: No other grants
- **WHEN** the SPA client requests the client credentials or password grant
- **THEN** the server refuses

### Requirement: No consent screen
The server SHALL grant the requested `openid`, `profile` and `email` scopes to the first-party SPA client without a consent step.

#### Scenario: Silent grant
- **WHEN** a signed-in user completes an authorization request from the SPA client
- **THEN** the redirect with the code happens without a consent page

### Requirement: Silent sign-in check
For an authorization request that carries `prompt=none`, names the registered `spa` client and a redirect URI registered for it, the server SHALL NOT show a sign-in form. When the browser has a valid session it SHALL answer as for a normal request (redirect with a code). When it has none it SHALL redirect to that redirect URI with `error=login_required` and the request's `state`, and no code.

#### Scenario: Valid session
- **WHEN** a signed-in browser sends a valid authorization request with `prompt=none`
- **THEN** it is redirected to the redirect URI with a `code` and the same `state`

#### Scenario: No session
- **WHEN** a browser without a session sends a valid authorization request with `prompt=none`
- **THEN** it is redirected to the registered redirect URI with `error=login_required` and the same `state`, the response is not the login form, and no code is issued

#### Scenario: Unregistered redirect URI
- **WHEN** a request with `prompt=none` uses a redirect URI that is not registered for the client
- **THEN** the server does not redirect to that URI and answers as it does without `prompt`

#### Scenario: Unknown client
- **WHEN** a request with `prompt=none` names a client that is not registered
- **THEN** the server does not redirect to the given URI

#### Scenario: Other prompt values
- **WHEN** a request has no `prompt`, or `prompt=login`, and no session
- **THEN** the browser is sent to the sign-in form as before

#### Scenario: No identity in the answer
- **WHEN** the server answers `login_required`
- **THEN** the redirect contains only `error` and `state`, and nothing about any user

### Requirement: Stable, opaque subject
The `sub` claim of every issued token SHALL be the user's internal id, an opaque UUID that is never reused for another person, and SHALL be identical for the same user whatever the login method.

#### Scenario: Form login
- **WHEN** a user signs in with email and password
- **THEN** `sub` in the access and ID tokens is that user's UUID, not the email

#### Scenario: Google login
- **WHEN** the same user signs in later with Google
- **THEN** `sub` is the same UUID

#### Scenario: Email is not the subject
- **WHEN** a user's email address changes in the future
- **THEN** `sub` does not change

### Requirement: Token claims
Access tokens SHALL carry `sub`, `iss`, `exp` and a `roles` claim listing the user's role names without a `ROLE_` prefix, and SHALL carry `email` and `name` when the `email` and `profile` scopes were granted and the values exist. Every registered user SHALL have the role `USER`.

#### Scenario: Default role
- **WHEN** a newly registered user obtains a token
- **THEN** `roles` is `["USER"]`

#### Scenario: Profile claims
- **WHEN** the SPA obtains a token with the `openid profile email` scopes for a user with a name
- **THEN** the access token contains `email` and `name`

#### Scenario: No password data
- **WHEN** any token is decoded
- **THEN** it contains no password, password hash or reset token

### Requirement: Token lifetimes
Access tokens SHALL be valid for 15 minutes. Refresh tokens SHALL be valid for 60 minutes.

#### Scenario: Access token expiry
- **WHEN** an access token is older than 15 minutes
- **THEN** the backend's validation rejects it as expired

#### Scenario: Refresh
- **WHEN** the SPA presents a valid refresh token
- **THEN** it receives a new access token

### Requirement: Logout
The server SHALL support OIDC RP-initiated logout to the registered post-logout URI and SHALL end the user's server-side session. When the end-session request cannot be validated (the ID token hint is unknown, for example replaced by a token refresh, or is missing), the server SHALL still end the session and redirect, but only to a post-logout URI registered for the client; any other request SHALL be answered 400. The rejection reason SHALL be logged as an error code and parameter name, never the token.

#### Scenario: Logout redirect
- **WHEN** the SPA calls the end-session endpoint with a valid ID token hint and the registered post-logout URI
- **THEN** the session ends and the browser is redirected to that URI

#### Scenario: Replaced ID token
- **WHEN** the SPA calls the end-session endpoint with an ID token that was replaced by a token refresh, and the registered post-logout URI
- **THEN** the session ends and the browser is redirected to that URI with the `state` it sent

#### Scenario: No ID token hint
- **WHEN** the end-session endpoint is called with the client id and the registered post-logout URI but no ID token hint
- **THEN** the session ends and the browser is redirected to that URI

#### Scenario: Unregistered URI
- **WHEN** the end-session request cannot be validated and names a post-logout URI that is not registered
- **THEN** the answer is 400 and there is no redirect

### Requirement: Own endpoints validate own tokens
The self-service endpoints under `/api/me/` SHALL require a valid access token issued by this server and SHALL reject any other request.

#### Scenario: No token
- **WHEN** `/api/me/password` is called without a token
- **THEN** the server responds 401

### Requirement: No credentials in logs
The server SHALL NOT write passwords, authorization codes, tokens, client secrets or `Authorization` header values to its logs.

#### Scenario: Failed login
- **WHEN** a login fails
- **THEN** the log contains no submitted password

### Requirement: Configured administrators
The auth service SHALL accept a configured list of administrator emails. A user who signed in through Google with an email that Google has verified, and whose email is on the list (compared ignoring case), SHALL have the `ADMIN` role in addition to `USER` in the `roles` claim of access tokens. Local-password accounts SHALL NOT receive `ADMIN` from this list. An empty or absent list SHALL grant nothing.

#### Scenario: Listed Google user
- **WHEN** a user signs in through Google with a verified email on the list
- **THEN** the access token's `roles` claim contains `USER` and `ADMIN`

#### Scenario: Case-insensitive match
- **WHEN** the list holds `Admin@Example.com` and Google reports `admin@example.com`
- **THEN** the user gets `ADMIN`

#### Scenario: Unlisted user
- **WHEN** a Google user whose email is not on the list signs in
- **THEN** the `roles` claim contains `USER` only

#### Scenario: Local account with a listed email
- **WHEN** a user signs in with a local password and an email that is on the list
- **THEN** the `roles` claim does not contain `ADMIN`

#### Scenario: Empty list
- **WHEN** no administrator emails are configured
- **THEN** no user gets `ADMIN` from this mechanism

#### Scenario: Removed from the list
- **WHEN** an email is removed from the list and its user signs in again
- **THEN** the new access token no longer contains `ADMIN`
