# ui-cv Specification

## Purpose
Defines the CV section of the profile page: managing the single CV and reviewing a draft taken from it before it is saved.

## Requirements

### Requirement: Review before saving
The profile page SHALL offer "Fill the profile from my CV" when the user has a master CV, and otherwise show the button disabled with a hint to choose a master CV in the cabinet. The button SHALL call `POST /api/cv/extract`, show a loading state, and put the returned draft into the profile form as unsaved changes, with a notice that nothing is saved until the user presses Save. Draft values SHALL replace the form only after the user confirms when the form already holds saved content. Errors (409, 413, 422, 429, 502, 503) SHALL each have a short fixed message.

#### Scenario: Draft fills the form
- **WHEN** the extraction succeeds
- **THEN** the form shows the draft values, the page says they are not saved, and `PUT /api/profile` has not been called

#### Scenario: No master
- **WHEN** the user has CV files but none is the master
- **THEN** the button is disabled and a hint says to choose a master CV

#### Scenario: Saved profile exists
- **WHEN** the user already has saved content and starts an extraction
- **THEN** a confirmation asks before the form is overwritten with the draft

#### Scenario: Scanned PDF
- **WHEN** the server answers 422
- **THEN** a message says no text could be read and suggests a text-based PDF, `.md` or `.txt`

#### Scenario: Rate limit
- **WHEN** the server answers 429
- **THEN** a message says to try again later
