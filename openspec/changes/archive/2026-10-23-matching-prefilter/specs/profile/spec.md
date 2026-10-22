# Spec Delta

## ADDED Requirements

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
