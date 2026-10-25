## ADDED Requirements

### Requirement: Track an imported job
The applications module SHALL create an application from a job that has no posting, storing the frozen copy with `source = IMPORT`.

#### Scenario: Live status
- **WHEN** an imported application is read
- **THEN** its live status is OPEN and `liveChanged` is false

#### Scenario: Scoping
- **WHEN** user B requests user A's imported application by id
- **THEN** the answer is 404

#### Scenario: Per-user uniqueness
- **WHEN** the same user imports the same canonical URL twice
- **THEN** one application exists
