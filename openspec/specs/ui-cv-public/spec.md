# ui-cv-public Specification

## Purpose
Defines the Public CV page of the web UI: choosing a handle, deciding what is public, previewing, publishing, pausing and removing the public CV page.

## Requirements

### Requirement: Public CV page
The UI SHALL provide a page at `/publish`, reachable from the signed-in shell and from the profile page, that loads `GET /api/cv/public` and shows: the handle field, the template (Classic, Modern, Compact) and language (English, Deutsch) selects, a checkbox for each section (Profile, Experience, Open source projects, Education, Skills, Languages, Publications) with move up/down, checkboxes for headline, analysis summaries, work authorisation, location, email, phone, website, LinkedIn and code-account links, and a checkbox "Allow search engines". A visitor who is not signed in SHALL see the landing page at that address. When nothing is configured, **every checkbox SHALL be unticked** and the sections list empty. The page SHALL say that the full name is always shown and that preferences are never shown.

#### Scenario: Defaults are private
- **WHEN** a signed-in user with no public page opens `/publish`
- **THEN** every checkbox is unticked, template is Classic, language English, and the text says the name is always shown

#### Scenario: Loads the saved state
- **WHEN** a user with a published page opens `/publish`
- **THEN** the handle, settings and the published state are shown as saved

#### Scenario: Signed out
- **WHEN** a visitor without a session opens `/publish`
- **THEN** the landing page is shown

### Requirement: Handle field
The handle field SHALL lower-case what is typed, check the form in the browser (3 to 30 characters of letters, digits and single hyphens) and, after the user stops typing for 400 ms, call `GET /api/cv/public/handle` and show "available" or the reason (invalid, reserved, taken, held). It SHALL show the address the page will have, as `<origin>/cv/<handle>` (or `<origin>/api/public/cv/<handle>` while the `/cv/` route is not available, one constant), and when the handle cannot be changed yet, the date it can. Server messages for the field SHALL be shown at it.

#### Scenario: Availability
- **WHEN** the user types `ada-example` and pauses
- **THEN** one availability request is sent and the result is shown

#### Scenario: Typing quickly
- **WHEN** the user types five characters within 400 ms
- **THEN** at most one availability request is sent, for the final value

#### Scenario: Invalid form
- **WHEN** the user types `Ad`
- **THEN** a message says it must be 3 to 30 characters and no availability request is sent

#### Scenario: Change not yet allowed
- **WHEN** the server says the handle can be changed from a future date
- **THEN** the handle field is disabled and shows that date

### Requirement: Save, publish, pause and remove
The page SHALL have a "Publish" button (saves handle and settings with `published: true`), a "Pause" button when published (saves with `published: false`), "Save" for settings changes, and a "Remove" button that asks for confirmation and calls `DELETE /api/cv/public`. A page that is live SHALL show a banner "Your CV is live at <address>. Every change you save in your profile shows there at once, in the fields ticked above." with a copy-link button. A 409 `name_required` SHALL tell the user to add their full name and link to the Contact section of the profile. A 409 for the handle (`handle_taken`, `handle_reserved`, `handle_held`, `handle_change_too_soon`) SHALL show a message at the handle field. A locked page SHALL say it was disabled by an administrator and disable Publish. 400 field messages SHALL show at their fields.

#### Scenario: Publish
- **WHEN** the user enters a handle, ticks Experience and presses Publish
- **THEN** `PUT /api/cv/public` is sent with `published: true` and those settings, and the live banner with the address appears

#### Scenario: Pause
- **WHEN** the page is live and the user presses Pause
- **THEN** the request has `published: false` and the banner says the page is not public

#### Scenario: Remove with confirmation
- **WHEN** the user presses Remove and confirms
- **THEN** `DELETE /api/cv/public` is sent and the page returns to the default state; declining sends nothing

#### Scenario: Name missing
- **WHEN** the server answers 409 `name_required`
- **THEN** the page tells the user to add their full name and links to `/profile#contact`

#### Scenario: Handle taken
- **WHEN** the server answers 409 `handle_taken`
- **THEN** the message appears at the handle field and the other edits stay

#### Scenario: Locked
- **WHEN** the state says `locked: true`
- **THEN** a notice says an administrator disabled the page and Publish is disabled

#### Scenario: Copy link
- **WHEN** the user presses the copy button of the banner
- **THEN** the address is copied and the button confirms it

### Requirement: Preview
A "Preview" button SHALL call `POST /api/cv/public/preview` with the current (possibly unsaved) settings and open the returned path in a new tab (`rel="noopener"`), with a short loading state and the fixed message for a failure. The page SHALL say that the preview is made from the saved profile and the settings on screen. The page SHALL NOT put the rendered CV into the app's DOM or a frame, and SHALL NOT store it in the browser.

#### Scenario: Open preview
- **WHEN** the user presses Preview
- **THEN** a preview request with the settings on screen is sent and a new tab opens at the returned path

#### Scenario: Preview failure
- **WHEN** the server answers 429
- **THEN** a fixed message says to try again later and no tab opens

### Requirement: Profile page shows the live state
The profile page SHALL show, next to the Contact section, a notice when the public CV is live ("Your public CV is live at <address>; saved changes show there at once") with a link to `/publish`, and a link "Public CV" when it is not. The notice SHALL be based on `GET /api/cv/public` and SHALL NOT block the page if that call fails.

#### Scenario: Live notice
- **WHEN** the user's page is published and they open the profile page
- **THEN** the notice with the address and the link is shown

#### Scenario: Not published
- **WHEN** the user has no public page
- **THEN** only the link "Public CV" is shown

#### Scenario: Call fails
- **WHEN** `GET /api/cv/public` fails
- **THEN** the profile page works as before and shows no notice

### Requirement: Public photo choice
The Public CV page SHALL offer the template "Professional" and, when it is chosen, a photo picker of the user's JPEG files with "No photo" selected by default. Next to the picker the page SHALL state that the photo becomes visible to everyone with the link. The choice is part of the saved settings and the preview. A `photo_invalid` answer SHALL show a message without the file name.

#### Scenario: Warning shown
- **WHEN** the owner picks a photo
- **THEN** the page says the photo is public once published

#### Scenario: Saved with the settings
- **WHEN** the owner saves with a photo
- **THEN** the request carries `photoFileId` and the preview shows the photo
