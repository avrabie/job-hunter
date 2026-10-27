## MODIFIED Requirements

### Requirement: Landing page for visitors
The UI SHALL show a signed-out visitor a landing page with a sign-in action and a register action, and no data that belongs to a user. The landing page MAY show the latest public postings, fetched without a token from the public postings endpoint; it SHALL make no other call to `/api`.

#### Scenario: Signed-out visit
- **WHEN** a visitor without a session opens `/`
- **THEN** the page offers "Sign in" and "Register" and makes no call to `/api` other than the public postings list

#### Scenario: No user data
- **WHEN** a visitor without a session opens `/`
- **THEN** nothing on the page comes from a user's profile, applications or files
