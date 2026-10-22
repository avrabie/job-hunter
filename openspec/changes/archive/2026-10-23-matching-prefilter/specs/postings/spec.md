# Spec Delta

## MODIFIED Requirements

### Requirement: JobIngested event
The system SHALL publish a `JobIngested` event when a canonical posting is created. The publisher is `postings`. The payload is `postingId` and `ingestedAt` (UTC timestamp). It carries no posting text. The consumer is `matching`, which evaluates the new posting for every user with a stored profile. The event SHALL NOT be published for `updated` or `unchanged` results, or when a posting is linked to an existing canonical posting.

#### Scenario: Event on creation
- **WHEN** a posting creates a new canonical row
- **THEN** exactly one `JobIngested` event with that `postingId` is published and recorded by the event publication registry

#### Scenario: No event on repeat
- **WHEN** the same posting is ingested again
- **THEN** no `JobIngested` event is published

#### Scenario: No event on cross-source link
- **WHEN** a posting from a second source is linked to an existing canonical posting
- **THEN** no `JobIngested` event is published
