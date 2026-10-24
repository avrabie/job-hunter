## MODIFIED Requirements

### Requirement: Editing lists
The page SHALL let the user add, remove and reorder experience, education, publications, skills, languages, achievements and picked open source projects, and SHALL send the whole profile on save.

#### Scenario: Add and remove
- **WHEN** the user adds an experience entry, fills it and removes another
- **THEN** the saved profile contains the added entry and not the removed one

### Requirement: Fill a section from the CV
The Experience, Education and Publications sections SHALL each have a "Fill from CV" button. It SHALL be enabled when the user has a master CV and otherwise disabled with a hint to choose one. It SHALL call `POST /api/cv/extract?section=<name>`, show a loading state in that section, and replace only that section's entries in the form with the returned ones, as unsaved changes, with a notice that nothing is saved until Save. When the section already has entries the page SHALL ask for confirmation first. Errors SHALL show the same fixed messages as the whole-profile button.

#### Scenario: Fill one section
- **WHEN** the user presses "Fill from CV" on Education
- **THEN** only the education entries change, the page says they are not saved, and no profile request is sent

#### Scenario: Existing entries
- **WHEN** the section already has entries and the user presses the button
- **THEN** a confirmation asks before they are replaced, and declining changes nothing

#### Scenario: No master
- **WHEN** the user has no master CV
- **THEN** the three buttons are disabled and a hint says to choose a master CV

#### Scenario: Error
- **WHEN** the server answers 422 or 429
- **THEN** a fixed message appears in that section and the entries are unchanged

#### Scenario: Fill projects
- **WHEN** the profile page is open
- **THEN** there is no "Projects" section and no "Fill from CV" button for projects

### Requirement: Technology tags
Experience entries SHALL have a Technologies field that takes tags: typing a name and pressing Enter or a comma adds it, each tag has a remove control, and Backspace in the empty input removes the last tag. Tags SHALL be trimmed, a blank tag ignored and a tag that differs from an existing one only in case ignored. The input SHALL suggest names taken from the user's skills and from tags already used in experience.

#### Scenario: Add tags
- **WHEN** the user types `Kotlin`, presses Enter, types `kotlin` and presses Enter
- **THEN** the field holds one tag, `Kotlin`

#### Scenario: Remove a tag
- **WHEN** the user presses the remove control of a tag
- **THEN** that tag is gone and the others stay in order

#### Scenario: Suggestions
- **WHEN** the user has the skill `PostgreSQL` and starts typing `Post` in a technologies field
- **THEN** `PostgreSQL` is offered as a suggestion

## REMOVED Requirements

### Requirement: Projects section
**Reason**: Replaced by the Open Source Projects section.
**Migration**: None in the UI; stored projects are dropped by the server on the next save.

## ADDED Requirements

### Requirement: Open Source Projects section
The page SHALL show a section titled "Open Source Projects" with a "Code accounts" part (a provider select, GitHub only for now, and a username field; at most one account per provider) and a list of the picked projects. Each picked project SHALL show its name (a link to the repository), description, language, topics and stars read-only, and an editable "Note" field, with remove and move up/down controls. Only picked projects SHALL be sent on save. The browser SHALL check the username rule when the field is left, and the server stays the authority.

#### Scenario: Add an account
- **WHEN** the user chooses GitHub, types `octocat` and saves
- **THEN** the request holds `codeAccounts` with that account

#### Scenario: Bad username
- **WHEN** the user leaves the username field holding `-bad-`
- **THEN** a message names that field, and if the profile is saved anyway the server's message is shown there

#### Scenario: Note and order
- **WHEN** the user types a note on a project, moves it up and saves
- **THEN** the saved project has that note and the new position

### Requirement: Pick repositories from a code account
A "Load repositories" control SHALL be enabled when an account has a valid username. It SHALL call `GET /api/profile/repositories` with that provider and username, show a loading state, and list the returned repositories as a checklist with name, description, language and stars, with repositories already picked shown ticked. Ticking adds the repository to the picked list and unticking removes it, as unsaved changes with a notice that nothing is saved until Save. The page SHALL show a fixed message for each failure (`invalid_username`, `account_not_found`, `provider_rate_limited`, `provider_unavailable`) and any other failure, and keep the picked list unchanged. Loading SHALL NOT send a profile request.

#### Scenario: Load and pick
- **WHEN** the user loads repositories and ticks two
- **THEN** the picked list holds those two, the page says they are not saved, and no profile request is sent

#### Scenario: Already picked
- **WHEN** a repository is already picked and the list loads
- **THEN** it is shown ticked, and unticking it removes it from the picked list

#### Scenario: Picked repository missing from the list
- **WHEN** a picked repository is not in the loaded list (renamed, archived or deleted)
- **THEN** it stays in the picked list until the user removes it

#### Scenario: Account not found
- **WHEN** the server answers 404 `account_not_found`
- **THEN** a fixed message appears in the section and the picked list is unchanged

#### Scenario: Rate limited
- **WHEN** the server answers 429
- **THEN** a fixed message says to try again later and the picked list is unchanged
