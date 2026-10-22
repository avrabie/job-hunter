# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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
