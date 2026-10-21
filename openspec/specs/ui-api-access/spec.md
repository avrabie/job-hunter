# ui-api-access Specification

## Purpose

Defines how the UI calls the backend API on the user's behalf: which credentials it sends, how it reacts when they are rejected, and what it never exposes.

## Requirements

### Requirement: Bearer token on API calls
Every request the UI makes to a path under `/api/` SHALL carry the current access token in the `Authorization: Bearer` header, and SHALL use the page's own origin.

#### Scenario: Authenticated call
- **WHEN** the signed-in shell calls `/api/`
- **THEN** the request goes to the page's own origin with the user's access token

#### Scenario: Token never sent elsewhere
- **WHEN** the UI makes a request to any other origin
- **THEN** it carries no `Authorization` header

### Requirement: Rejected token starts a new sign-in
When an API call is answered with 401 the UI SHALL try one token renewal and repeat the call once; if that also fails it SHALL treat the user as signed out.

#### Scenario: Expired access token
- **WHEN** a call is answered 401 and the renewal succeeds
- **THEN** the call is repeated once with the new token and its result is used

#### Scenario: Still unauthorized
- **WHEN** the repeated call is answered 401 again
- **THEN** the tokens are discarded and the landing page is shown

### Requirement: Other API failures are shown, not hidden
The UI SHALL tell the user when an API call fails for any reason other than 401 and SHALL NOT show response bodies or token values in the message.

#### Scenario: Backend without endpoints
- **WHEN** the shell's `/api/` call is answered 404
- **THEN** it shows that the backend has no endpoints yet

#### Scenario: Backend unreachable
- **WHEN** the call is answered 502, 503 or fails to connect
- **THEN** it shows that the service is unavailable and offers to retry

### Requirement: No sensitive values in logs
The UI SHALL NOT write tokens, the user's email or name, or response bodies to the browser console.

#### Scenario: Console during sign-in
- **WHEN** a user signs in and an API call fails
- **THEN** the console output contains no token, email, name or response body
