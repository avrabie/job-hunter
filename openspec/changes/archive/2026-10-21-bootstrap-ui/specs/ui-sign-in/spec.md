# Spec Delta

## Purpose

Defines how a visitor registers, signs in, stays signed in and signs out of the web UI, and where the UI keeps credentials. Sign-in always happens at our own auth service.

## ADDED Requirements

### Requirement: Landing page for visitors
The UI SHALL show a signed-out visitor a landing page with a sign-in action and a register action, and no data that belongs to a user.

#### Scenario: Signed-out visit
- **WHEN** a visitor without a session opens `/`
- **THEN** the page offers "Sign in" and "Register" and makes no call to `/api`

### Requirement: Sign-in at the auth service
The UI SHALL sign a user in with the authorization code flow with PKCE against the `spa` client of the auth service, requesting the scopes `openid profile email`, and SHALL never see or handle the user's password.

#### Scenario: Sign in
- **WHEN** the visitor chooses "Sign in" and enters valid credentials at the auth service
- **THEN** the browser returns to `/callback`, the UI exchanges the code for tokens and shows the signed-in shell

#### Scenario: State mismatch
- **WHEN** `/callback` is opened with a `state` that the UI did not issue
- **THEN** no tokens are requested and the UI shows an error with a way to start again

#### Scenario: Sign-in refused
- **WHEN** the auth service returns an error to `/callback`
- **THEN** the UI shows that sign-in failed, without the error details from the URL, and offers to try again

### Requirement: Registration
The UI SHALL offer a registration form for email, password and optional first and last name that calls the auth service's registration endpoint, and on success SHALL start the sign-in.

#### Scenario: Successful registration
- **WHEN** the visitor submits valid values
- **THEN** the account is created and the browser goes to the auth service sign-in

#### Scenario: Email already taken
- **WHEN** the endpoint answers 409
- **THEN** the form says the email is already registered and keeps the other values

#### Scenario: Invalid values
- **WHEN** the endpoint answers 400 with field errors
- **THEN** each message appears next to its field

### Requirement: Tokens kept in memory only
The UI SHALL keep access, ID and refresh tokens in JavaScript memory only, and SHALL NOT write any token or personal value to `localStorage`, `sessionStorage`, IndexedDB or cookies.

#### Scenario: Nothing persisted
- **WHEN** a user has signed in
- **THEN** the page's `localStorage`, `sessionStorage` and cookies contain no token, email or name

### Requirement: Session survives a reload
After a page reload the UI SHALL restore the signed-in state without asking for credentials while the session at the auth service is valid. When that session has ended, the user SHALL arrive at the auth service's sign-in form and, after signing in, return to the UI.

#### Scenario: Reload with a valid session
- **WHEN** a signed-in user reloads the page
- **THEN** the UI returns to the signed-in shell without showing the login form

#### Scenario: Reload after the session ended
- **WHEN** a user who signed in earlier in this browser reloads after the session at the auth service has expired
- **THEN** the browser shows the auth service's sign-in form, and after signing in the UI shows the signed-in shell

#### Scenario: First visit
- **WHEN** a browser that has never signed in opens `/`
- **THEN** the UI shows the landing page without redirecting

### Requirement: Token renewal
The UI SHALL renew the access token with the refresh token before it expires, and SHALL treat a failed renewal as signed out.

#### Scenario: Renewal succeeds
- **WHEN** the access token is close to expiry
- **THEN** a new access token is obtained without user action

#### Scenario: Renewal fails
- **WHEN** the refresh token is rejected
- **THEN** the in-memory tokens are discarded and the landing page is shown

### Requirement: Sign-out
The UI SHALL end the session at the auth service when the user signs out, discard all in-memory tokens, and return to the landing page.

#### Scenario: Sign out
- **WHEN** a signed-in user chooses "Sign out"
- **THEN** the auth service session ends and a later visit to `/` shows the landing page without an automatic sign-in

### Requirement: Signed-in shell shows the identity
The signed-in shell SHALL show the user's email or name from the ID token and SHALL NOT display the token values.

#### Scenario: Identity shown
- **WHEN** a user is signed in
- **THEN** the shell shows their email or name and no token text appears in the page
