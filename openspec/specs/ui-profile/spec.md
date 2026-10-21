# ui-profile Specification

## Purpose

Defines the profile page of the web UI: how a signed-in user views, edits and saves the master profile and how problems are shown.

## Requirements

### Requirement: Profile page for signed-in users
The UI SHALL provide a page at `/profile`, reachable from the signed-in shell, that loads the profile with `GET /api/profile` through the API client and shows all sections of the profile as editable fields. A visitor who is not signed in SHALL see the landing page at that address, with no automatic redirect, and SHALL return to `/profile` after choosing "Sign in".

#### Scenario: Load
- **WHEN** a signed-in user opens `/profile`
- **THEN** the page shows the stored profile, or empty sections when the version is 0

#### Scenario: Signed out
- **WHEN** a visitor without a session opens `/profile`
- **THEN** the landing page is shown, no sign-in starts by itself, and after the visitor chooses "Sign in" and signs in the UI opens `/profile`

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

### Requirement: Field checks while editing
The page SHALL check the cheap rules in the browser and show the message next to the field as soon as the user leaves it or, once a message is shown, as they type. The rules are: required text not blank, canton two uppercase letters, language code two lowercase letters, end month not before start, workload between 10 and 100 with minimum not above maximum, salary not negative.

#### Scenario: Message on leaving a field
- **WHEN** the user types `zh` in the canton field and moves to the next field
- **THEN** the message "must be two uppercase letters" appears at the canton field without saving

#### Scenario: Message clears when fixed
- **WHEN** a field shows a message and the user changes it to a valid value
- **THEN** the message disappears while typing

#### Scenario: Server stays the authority
- **WHEN** the browser finds no problem but the server answers 400 with a field message
- **THEN** the server's message is shown at that field

### Requirement: Focus after a failed save
After a save is answered with field messages, the first field with a message SHALL receive focus.

#### Scenario: Failed save moves focus
- **WHEN** a save is answered with field messages
- **THEN** the first field with a message is focused and scrolled into view

### Requirement: Leaving with unsaved edits
The page SHALL warn before the user leaves it or closes the tab while there are unsaved edits.

#### Scenario: Unsaved edits
- **WHEN** the user has changed a field and tries to close the tab
- **THEN** the browser asks for confirmation; after a successful save it does not
