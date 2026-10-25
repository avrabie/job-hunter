## MODIFIED Requirements

### Requirement: Landing page for visitors
The UI SHALL show a signed-out visitor a landing page that explains what the product does, with a sign-in action and a register action, and no data that belongs to a user.

#### Scenario: Signed-out visit
- **WHEN** a visitor without a session opens `/`
- **THEN** the page offers "Sign in" and "Register" and makes no call to `/api`

#### Scenario: Content
- **WHEN** the landing page is shown
- **THEN** it has a hero with a one-sentence description, capability cards for job matching, job tracking, motivation letters, CV export, the public CV page and importing a job from a URL, a three-step "how it works", and a note that the user reviews everything and nothing is sent to employers

#### Scenario: Honest claims
- **WHEN** the capability cards are compared with the shipped features
- **THEN** each card describes a feature that exists

### Requirement: Signed-in shell shows the identity
The signed-in shell SHALL show the user's email or name from the ID token in the top bar and SHALL NOT display the token values. The signed-in home page SHALL be a short overview with links to the main areas and the number of tracked jobs per stage.

#### Scenario: Identity shown
- **WHEN** a user is signed in
- **THEN** the top bar shows their email or name and no token text appears in the page

#### Scenario: Overview
- **WHEN** a signed-in user opens `/`
- **THEN** the page shows cards for Matches, Job tracking (with counts per stage) and Profile, and a small service-status line in the footer
