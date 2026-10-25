# ui-applications Specification

## Purpose
TBD - created by archiving change job-tracking-and-letters. Update Purpose after archive.

## Requirements

### Requirement: Job Tracking page
The UI SHALL have a page `/applications` (nav entry "Job tracking", signed-in only) that lists the user's applications grouped by stage (Interested, Applying, Applied, Rejected) with company, title, dates and a warning mark when the live posting changed or disappeared. Each row opens the application page; the stage can be changed from the row.

#### Scenario: Grouped
- **WHEN** the user has applications in three stages
- **THEN** each appears under its stage

### Requirement: Application page
`/applications/:id` SHALL show the frozen description (plain text), the link to the original posting, the stage, dates and notes (editable and saved), the live-posting notice, a remove action with confirmation, and the letter workspace.

#### Scenario: Notes saved
- **WHEN** the user edits the notes and saves
- **THEN** the page shows them after a reload

### Requirement: Letter workspace
The workspace SHALL let the user choose language, experience entries, projects and skills from their saved profile (nothing chosen by default except none; the user must choose at least one entry), add instructions and generate. It SHALL show the current version as Markdown text with numbered paragraphs, a comment box for the whole letter and for each paragraph, buttons Regenerate (with comments), Edit (a text area, Save as new version), Copy and Download `.md`, and a version list to reopen older versions. It SHALL show the notice "Check every statement before you send this letter; the writing assistant can get facts wrong." and the fixed messages for 429, 502 and 503. The text SHALL be rendered as text, never as HTML.

#### Scenario: Regenerate
- **WHEN** the user types a comment on paragraph 2 and presses Regenerate
- **THEN** a new version appears with the comment sent, and the old version stays in the list

#### Scenario: Not configured
- **WHEN** the backend answers 503
- **THEN** the page says the writing assistant is not available

#### Scenario: Notice
- **WHEN** a letter is shown
- **THEN** the checking notice is visible
