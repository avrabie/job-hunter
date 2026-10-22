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
