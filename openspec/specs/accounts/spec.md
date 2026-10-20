# accounts Specification

## Purpose
Identifies the caller of the API from the auth service JWT, keeps a user record per `sub`, and defines the rule that all per-user data is scoped by that user id.

## Requirements

### Requirement: Authenticated API access
The system SHALL require a valid JWT, issued by the configured auth service issuer, for every request under `/api/`.

#### Scenario: No token
- **WHEN** a request to a path under `/api/` has no `Authorization` header
- **THEN** the system responds 401

#### Scenario: Token from another issuer
- **WHEN** a request carries a JWT whose `iss` is not the configured issuer
- **THEN** the system responds 401

#### Scenario: Expired token
- **WHEN** a request carries a JWT whose `exp` is in the past
- **THEN** the system responds 401

#### Scenario: Valid token
- **WHEN** a request carries a valid JWT from the configured issuer
- **THEN** the request is processed as the user identified by the token's `sub`

### Requirement: Roles from token
The system SHALL map the roles claim of the JWT to authorities, and SHALL reject access to a role-restricted endpoint when the required role is absent.

#### Scenario: Role present
- **WHEN** a token with the role `ADMIN` calls an endpoint restricted to `ADMIN`
- **THEN** the system allows the call

#### Scenario: Role missing
- **WHEN** a token without the role `ADMIN` calls an endpoint restricted to `ADMIN`
- **THEN** the system responds 403

### Requirement: Current user resolution
The system SHALL expose the current user's id, equal to the JWT `sub`, to other modules through the `accounts` public API, and SHALL NOT let other modules read `sub` from the token themselves.

#### Scenario: Id available inside a request
- **WHEN** an authenticated request is handled
- **THEN** the `accounts` public API returns the `sub` of that request's token as the current user id

#### Scenario: No current user outside a request
- **WHEN** code asks for the current user id with no authenticated request in progress
- **THEN** the call fails with an error instead of returning a default or empty user

### Requirement: User provisioning on first sight
The system SHALL create a user record the first time it sees a valid `sub` with no existing record, including users who signed in with Google at the auth server for the first time, and SHALL NOT create a second record for a later request with the same `sub`.

#### Scenario: First request of a new user
- **WHEN** a valid token with an unknown `sub` calls the API
- **THEN** one user record exists for that `sub`, and the call is processed normally

#### Scenario: Repeat request
- **WHEN** the same user calls the API again
- **THEN** no additional user record is created and the existing one is kept

#### Scenario: Concurrent first requests
- **WHEN** two requests with the same new `sub` arrive at the same time
- **THEN** exactly one user record exists afterwards and neither request fails

#### Scenario: Profile claims
- **WHEN** the token carries `email` or `name` claims
- **THEN** they are stored on the user record, and are absent from it when the token does not carry them

### Requirement: UserRegistered event
The system SHALL publish a `UserRegistered` event, once per user, when a user record is created. The publisher is `accounts`. The payload is `userId` (the `sub`) and `registeredAt` (UTC timestamp). It carries no email or name. There are no consumers yet; later modules (for example `profile`) may subscribe.

#### Scenario: Event on creation
- **WHEN** a user record is created for a new `sub`
- **THEN** exactly one `UserRegistered` event with that `userId` is published, and the event publication registry records it for every listener that handles it

#### Scenario: No event on repeat
- **WHEN** an existing user calls the API again
- **THEN** no `UserRegistered` event is published

### Requirement: Per-user data isolation
Every query on per-user data SHALL be scoped by the current user id, and the system SHALL NOT return, change or reveal the existence of another user's data. Shared data (postings and their embeddings) is the only exception. Each later per-user capability SHALL include a scenario proving this.

#### Scenario: User A cannot read user B
- **WHEN** user B owns a per-user record and user A requests it by its id
- **THEN** the system responds 404, the same response as for an id that does not exist

#### Scenario: Listing is scoped
- **WHEN** users A and B each own records and user A lists them
- **THEN** the response contains only A's records

### Requirement: No credentials in logs
The system SHALL NOT write JWTs or `Authorization` header values to its logs, including on authentication failure.

#### Scenario: Rejected token
- **WHEN** a request with an invalid token is rejected
- **THEN** no log line contains the token value
