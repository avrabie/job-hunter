# Spec Delta

## Purpose

Defines the profile page of the web UI: how a signed-in user views, edits and saves the master profile and how problems are shown.

## ADDED Requirements

### Requirement: Profile page for signed-in users
The UI SHALL provide a page at `/profile`, reachable from the signed-in shell, that loads the profile with `GET /api/profile` through the API client and shows all sections of the profile as editable fields. A visitor who is not signed in SHALL be sent through the normal sign-in instead of seeing the page.

#### Scenario: Load
- **WHEN** a signed-in user opens `/profile`
- **THEN** the page shows the stored profile, or empty sections when the version is 0

#### Scenario: Signed out
- **WHEN** a visitor without a session opens `/profile`
- **THEN** the sign-in starts, and after sign-in the visitor returns to `/profile`

### Requirement: Editing lists
The page SHALL let the user add, remove and reorder experience, education, skills, languages and achievements, and SHALL send the whole profile on save.

#### Scenario: Add and remove
- **WHEN** the user adds an experience entry, fills it and removes another
- **THEN** the saved profile contains the added entry and not the removed one

### Requirement: Saving
Saving SHALL call `PUT /api/profile` with the version that was loaded. On success the page SHALL show the saved state and the new version.

#### Scenario: Successful save
- **WHEN** the user saves valid edits
- **THEN** the page shows that the profile is saved and later saves use the new version

### Requirement: Errors and conflicts
On 400 the page SHALL show each message next to its field. On 409 it SHALL tell the user the profile changed elsewhere, keep the unsaved edits on screen, and offer to reload the stored profile. On any other failure it SHALL show the generic message from the API client and keep the edits.

#### Scenario: Field error
- **WHEN** the API answers 400 for `experience[0].title`
- **THEN** the message appears at that field and the other edits stay

#### Scenario: Conflict
- **WHEN** the API answers 409
- **THEN** the page shows the conflict notice, the edits stay, and "Reload" replaces them with the stored profile

### Requirement: Profile never kept in the browser
The UI SHALL NOT store the profile in `localStorage`, `sessionStorage` or cookies and SHALL NOT log it.

#### Scenario: No browser storage
- **WHEN** a profile is loaded, edited and saved
- **THEN** no profile text is present in any browser storage or console output

### Requirement: Leaving with unsaved edits
The page SHALL warn before the user leaves it or closes the tab while there are unsaved edits.

#### Scenario: Unsaved edits
- **WHEN** the user has changed a field and tries to close the tab
- **THEN** the browser asks for confirmation; after a successful save it does not
