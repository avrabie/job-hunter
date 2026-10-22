# profile Specification

## Purpose

Defines the per-user master profile: the facts about a person that matching and document drafting may use, how it is stored and protected, and the API to read and replace it.

## Requirements

### Requirement: One master profile per user
The system SHALL store at most one master profile per user, keyed by the `sub` of the caller's token, and SHALL scope every read and write by that `sub`. No endpoint SHALL accept a user id.

#### Scenario: Empty profile for a new user
- **WHEN** a user who never saved a profile calls `GET /api/profile`
- **THEN** the response is 200 with `version` 0 and every list empty and every optional field null

#### Scenario: Save and read back
- **WHEN** a user saves a valid profile and then calls `GET /api/profile`
- **THEN** the response contains the saved content and `version` 1

#### Scenario: User A cannot reach user B's profile
- **WHEN** users A and B each saved a profile and A calls `GET /api/profile` and `PUT /api/profile`
- **THEN** A sees and changes only A's profile, and B's profile is unchanged

#### Scenario: Anonymous call
- **WHEN** `GET /api/profile` or `PUT /api/profile` is called without a valid token
- **THEN** the response is 401

### Requirement: Profile content
The profile SHALL be language-neutral and SHALL contain `location`, `workAuthorisation`, `languages`, `summary`, `experience`, `education`, `skills` and `preferences` (target roles, workload range, remote modes, cantons, minimum yearly salary in CHF). Experience and education entries carry `YYYY-MM` months; an entry without an end month is current.

#### Scenario: Round trip keeps every field
- **WHEN** a profile that uses every field is saved and read back
- **THEN** the returned content equals what was saved, with entries in the same order

#### Scenario: Current position
- **WHEN** an experience entry has no end month
- **THEN** it is accepted and returned with a null end month

### Requirement: Profile value sets
`workAuthorisation` SHALL be one of `SWISS_CITIZEN`, `PERMIT_C`, `PERMIT_B`, `PERMIT_L`, `PERMIT_G`, `NEEDS_SPONSORSHIP` or null. A language SHALL have a two-letter lowercase code and a level `A1` to `C2` or `NATIVE`. A skill level SHALL be `BEGINNER`, `INTERMEDIATE`, `ADVANCED` or `EXPERT`. Remote modes SHALL be `ONSITE`, `HYBRID` or `REMOTE`. Cantons SHALL be two-letter codes.

#### Scenario: Permit values
- **WHEN** each listed `workAuthorisation` value is saved
- **THEN** it is accepted, and any other value is rejected with 400

### Requirement: Validation and limits
The system SHALL reject a profile that breaks a rule with status 400 and a body `{"error":"validation_failed","fields":{"<path>":"<message>"}}`. Rules: required text not blank; start month not after end month; workload between 10 and 100 with minimum not above maximum; salary not negative; unknown enum values and unknown fields rejected.

#### Scenario: Blank required field
- **WHEN** an experience entry has a blank title
- **THEN** the response is 400 and `fields` contains `experience[0].title`

#### Scenario: Dates out of order
- **WHEN** an entry starts after it ends
- **THEN** the response is 400 and names the end field

#### Scenario: Unknown value
- **WHEN** `workAuthorisation` is `"CITIZEN"` or an unknown top-level field is sent
- **THEN** the response is 400 and nothing is stored

### Requirement: Size limits
Name-like fields SHALL be at most 200 characters, the summary 4000 and each achievement 600. A profile SHALL have at most 30 experience entries, 20 achievements per entry, 20 education entries, 100 skills, 10 languages, 10 target roles and 30 cantons.

#### Scenario: Too many entries
- **WHEN** a profile has 31 experience entries
- **THEN** the response is 400 and nothing is stored

#### Scenario: Too long text
- **WHEN** a summary has 4001 characters
- **THEN** the response is 400 and nothing is stored

### Requirement: Safe error messages and body size
Validation messages SHALL describe the rule and SHALL NOT contain the submitted value. A request body larger than 128 KB SHALL be rejected with 413 before it is parsed into a profile.

#### Scenario: Value not echoed
- **WHEN** a too-long summary made of a recognisable marker text is rejected
- **THEN** the response body does not contain the marker text

#### Scenario: Oversized body
- **WHEN** the request body is larger than 128 KB
- **THEN** the response is 413

### Requirement: Whole-document replace with a version check
The request and response body SHALL be `{"version": n, "profile": {...}}`. `PUT /api/profile` SHALL replace the caller's whole profile when `version` equals the stored version (0 when nothing is stored), store it with the version increased by one, and return the saved profile. Otherwise it SHALL return 409 with `{"error":"version_conflict"}` and change nothing.

#### Scenario: First save
- **WHEN** a user with no stored profile sends a valid profile with `version` 0
- **THEN** the response is 200 with `version` 1

#### Scenario: Update
- **WHEN** the stored version is 3 and the client sends `version` 3
- **THEN** the response is 200 with `version` 4

#### Scenario: Stale save
- **WHEN** the stored version is 4 and the client sends `version` 3
- **THEN** the response is 409 and the stored profile is unchanged

#### Scenario: Two saves race
- **WHEN** two requests with the same `version` arrive at the same time
- **THEN** exactly one succeeds and the other gets 409

### Requirement: Profile data is personal data
The profile SHALL be stored only in the `profile` table, which SHALL reference `app_user(sub)` with `ON DELETE CASCADE`, so deleting a user deletes the profile. The system SHALL NOT write any profile content to logs, exception messages or metrics, and SHALL NOT include profile content in any module event.

#### Scenario: Deleting the user removes the profile
- **WHEN** a user row is deleted
- **THEN** the user's profile row is gone

#### Scenario: Nothing in logs
- **WHEN** a profile is saved, rejected for validation, or rejected for a version conflict
- **THEN** the captured application log contains none of the submitted text

### Requirement: ProfileSaved event
The system SHALL publish a `ProfileSaved` event after a profile is replaced successfully. The publisher is `profile`. The payload is `userId` (the token's `sub`) and `version` (the new stored version). It carries no profile content. The consumer is `matching`. The event SHALL NOT be published when the save is rejected (validation failure or version conflict).

#### Scenario: Event on save
- **WHEN** a user saves a valid profile
- **THEN** exactly one `ProfileSaved` event with that user's id and the new version is published and recorded by the event publication registry

#### Scenario: No event on rejection
- **WHEN** a save fails validation or conflicts on the version
- **THEN** no `ProfileSaved` event is published

#### Scenario: No content in the event
- **WHEN** the event is published
- **THEN** its payload holds only the user id and the version

### Requirement: Stored profiles readable by other modules
The `profile` module SHALL expose, through its public API only, a page-by-page listing of all stored profiles with their owners' ids, for modules that act for every user (such as `matching`). It SHALL return only profiles that were saved, in a stable order, and SHALL NOT be reachable over HTTP.

#### Scenario: Listing
- **WHEN** another module lists stored profiles and two users have saved one
- **THEN** it receives those two profiles with their owner ids, and no user who never saved a profile

#### Scenario: Not an endpoint
- **WHEN** a client requests any HTTP path for this listing
- **THEN** no such path exists
