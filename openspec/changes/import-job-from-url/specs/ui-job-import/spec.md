## ADDED Requirements

### Requirement: Import form
The Job tracking page SHALL offer "Add job from URL" with a URL field and, after an `unreadable`, `not_html` or `blocked` failure, a text area for pasting the description.

#### Scenario: Successful preview
- **WHEN** the user submits a readable URL
- **THEN** an editable review form shows title, company, location and description with a "Save and track" button

#### Scenario: Failure with paste hint
- **WHEN** the server answers `unreadable`
- **THEN** the page shows the fixed message and reveals the paste field

#### Scenario: Confirm
- **WHEN** the user presses "Save and track"
- **THEN** the app opens the new application's page, or the existing one with a note that it was already tracked

#### Scenario: Shared notice
- **WHEN** the review form is shown
- **THEN** it states that the job will be visible to all users as "Added by a user"

#### Scenario: Rate limited
- **WHEN** the server answers `rate_limited`
- **THEN** the page shows the fixed message and keeps the entered URL
