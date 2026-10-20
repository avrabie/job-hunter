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
The server SHALL support OIDC RP-initiated logout to the registered post-logout URI and SHALL end the user's server-side session.

#### Scenario: Logout redirect
- **WHEN** the SPA calls the end-session endpoint with a valid ID token hint and the registered post-logout URI
- **THEN** the session ends and the browser is redirected to that URI

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
