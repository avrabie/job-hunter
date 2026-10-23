# ui-cv Specification

## Purpose
Defines the CV section of the profile page: managing the single CV and reviewing a draft taken from it before it is saved.

## Requirements

### Requirement: CV section
The profile page SHALL show a CV section that loads `GET /api/cv` through the API client. Without a CV it SHALL offer an upload control for `.pdf`, `.md` and `.txt` files. With a CV it SHALL show the file name, size and upload date, a Download button, a Replace control and a Delete button that asks for confirmation. Server rejections (400, 413) SHALL be shown as short fixed messages.

#### Scenario: No CV
- **WHEN** a user without a CV opens the profile page
- **THEN** the section offers an upload and shows no file details

#### Scenario: Upload
- **WHEN** the user chooses a valid file
- **THEN** the file details appear after the upload

#### Scenario: Rejected file
- **WHEN** the server answers 413
- **THEN** a message says the file is too large and the previous CV is still shown

#### Scenario: Download
- **WHEN** the user presses Download
- **THEN** the browser saves the file under its stored name, fetched with the user's own token

#### Scenario: Delete
- **WHEN** the user confirms the deletion
- **THEN** the section returns to the upload state

### Requirement: Review before saving
The section SHALL offer "Fill the profile from my CV" when a CV exists. It SHALL call `POST /api/cv/extract`, show a loading state, and put the returned draft into the profile form as unsaved changes, with a notice that nothing is saved until the user presses Save. Draft values SHALL replace the form only after the user confirms when the form already holds saved content. Errors (404, 413, 422, 429, 502) SHALL each have a short fixed message.

#### Scenario: Draft fills the form
- **WHEN** the extraction succeeds
- **THEN** the form shows the draft values, the page says they are not saved, and `PUT /api/profile` has not been called

#### Scenario: Saved profile exists
- **WHEN** the user already has saved content and starts an extraction
- **THEN** a confirmation asks before the form is overwritten with the draft

#### Scenario: Scanned PDF
- **WHEN** the server answers 422
- **THEN** a message says no text could be read and suggests a text-based PDF, `.md` or `.txt`

#### Scenario: Rate limit
- **WHEN** the server answers 429
- **THEN** a message says to try again later
