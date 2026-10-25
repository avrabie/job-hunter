# applications Specification

## Purpose
TBD - created by archiving change job-tracking-and-letters. Update Purpose after archive.

## Requirements

### Requirement: Track a posting
A signed-in user SHALL be able to start tracking a posting by its id. This creates the user's application with the stage `INTERESTED` and a frozen copy of the posting's title, company, original link, plain-text description and the time of capture. Tracking the same posting again SHALL return the existing application unchanged (idempotent). An unknown posting id SHALL answer 404. The application belongs to the user named by the token's `sub`; no request field names an owner.

#### Scenario: Interested
- **WHEN** a user marks a posting interested
- **THEN** an application with stage INTERESTED and the frozen description exists in the user's list

#### Scenario: Twice
- **WHEN** the same user marks the same posting twice
- **THEN** there is still one application and its stage and notes are unchanged

#### Scenario: Unknown posting
- **WHEN** the id matches no posting
- **THEN** the answer is 404

### Requirement: Frozen description
The frozen copy SHALL NOT change when the shared posting is refreshed, changes or is no longer crawled. The application SHALL also tell whether the live posting still exists, when it was last seen, and whether its description differs from the frozen copy, so the page can warn the user.

#### Scenario: Posting changes
- **WHEN** the shared posting's description changes after the user tracked it
- **THEN** the application's description is the old text and `liveChanged` is true

### Requirement: Stages and notes
The stage SHALL be one of `INTERESTED`, `APPLYING`, `APPLIED`, `REJECTED` and can be changed to any other at any time. Changing it records the time of the change; entering `APPLIED` also records the applied date (editable). Free notes are limited to 5 000 characters. Unknown stages and over-long notes SHALL answer 400.

#### Scenario: Move to applied
- **WHEN** the user sets the stage to APPLIED
- **THEN** the stage, the change time and the applied date are stored

### Requirement: Own applications only
Every read and write SHALL be limited to the caller's own applications. An application of another user SHALL answer 404, the same as an unknown id. A user SHALL have at most 500 applications; the 501st SHALL answer 409.

#### Scenario: Other user
- **WHEN** user B reads or changes user A's application id
- **THEN** the answer is 404

### Requirement: List and remove
The list SHALL return the caller's applications newest change first with a stage filter, without the full description (title, company, stage, dates, live status). Removing an application deletes it and its letters at once.

#### Scenario: Remove
- **WHEN** the user removes an application that has letters
- **THEN** the application and its letters are gone
