# Spec Delta

## MODIFIED Requirements

### Requirement: Session survives a reload
After a page reload the UI SHALL restore the signed-in state without asking for credentials while the session at the auth service is valid. It SHALL ask quietly (`prompt=none`) and SHALL NOT show the auth service's sign-in form for this. When that session has ended, the UI SHALL show the landing page, clear its "signed in before" flag, and wait for the user to choose "Sign in".

#### Scenario: Reload with a valid session
- **WHEN** a signed-in user reloads the page
- **THEN** the UI returns to the signed-in shell without showing the login form

#### Scenario: Reload after the session ended
- **WHEN** a user who signed in earlier in this browser reloads after the session at the auth service has expired
- **THEN** the UI shows the landing page, not the auth service's sign-in form, and the user signs in only after choosing "Sign in"

#### Scenario: First visit
- **WHEN** a browser that has never signed in opens `/`
- **THEN** the UI shows the landing page without redirecting

#### Scenario: Reload keeps the page
- **WHEN** a signed-in user reloads while on `/profile`
- **THEN** the UI returns to `/profile` signed in

#### Scenario: Reload keeps the landing page when the session ended
- **WHEN** the session has ended and the user reloads while on `/profile`
- **THEN** the UI shows the landing page at `/profile` and, after the user chooses "Sign in", returns to `/profile`

## ADDED Requirements

### Requirement: Return to the page that was open
After a sign-in the UI SHALL open the page the user was on or asked for, but only if it is one of the UI's own pages from a fixed list. Any other value SHALL lead to the home page.

#### Scenario: Own page
- **WHEN** a signed-out visitor on `/profile` chooses "Sign in"
- **THEN** after signing in the UI opens `/profile`

#### Scenario: Foreign address
- **WHEN** the stored return value is an address outside the UI's list, such as `https://evil.example/` or `//evil.example`
- **THEN** after signing in the UI opens the home page
