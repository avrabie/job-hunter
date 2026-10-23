# Spec Delta

## MODIFIED Requirements

### Requirement: Editing lists
The page SHALL let the user add, remove and reorder experience, education, publications, projects, skills, languages, achievements, results and links, and SHALL send the whole profile on save.

#### Scenario: Add and remove
- **WHEN** the user adds an experience entry, fills it and removes another
- **THEN** the saved profile contains the added entry and not the removed one

### Requirement: Fill a section from the CV
The Experience, Education, Publications and Projects sections SHALL each have a "Fill from CV" button. It SHALL be enabled when the user has a master CV and otherwise disabled with a hint to choose one. It SHALL call `POST /api/cv/extract?section=<name>`, show a loading state in that section, and replace only that section's entries in the form with the returned ones, as unsaved changes, with a notice that nothing is saved until Save. When the section already has entries the page SHALL ask for confirmation first. Errors SHALL show the same fixed messages as the whole-profile button.

#### Scenario: Fill one section
- **WHEN** the user presses "Fill from CV" on Education
- **THEN** only the education entries change, the page says they are not saved, and no profile request is sent

#### Scenario: Fill projects
- **WHEN** the user presses "Fill from CV" on Projects
- **THEN** only the projects change, the page says they are not saved, and no profile request is sent

#### Scenario: Existing entries
- **WHEN** the section already has entries and the user presses the button
- **THEN** a confirmation asks before they are replaced, and declining changes nothing

#### Scenario: No master
- **WHEN** the user has no master CV
- **THEN** the four buttons are disabled and a hint says to choose a master CV

#### Scenario: Error
- **WHEN** the server answers 422 or 429
- **THEN** a fixed message appears in that section and the entries are unchanged

## ADDED Requirements

### Requirement: Technology tags
Experience entries and projects SHALL have a Technologies field that takes tags: typing a name and pressing Enter or a comma adds it, each tag has a remove control, and Backspace in the empty input removes the last tag. Tags SHALL be trimmed, a blank tag ignored and a tag that differs from an existing one only in case ignored. The input SHALL suggest names taken from the user's skills and from tags already used in experience and projects.

#### Scenario: Add tags
- **WHEN** the user types `Kotlin`, presses Enter, types `kotlin` and presses Enter
- **THEN** the field holds one tag, `Kotlin`

#### Scenario: Remove a tag
- **WHEN** the user presses the remove control of a tag
- **THEN** that tag is gone and the others stay in order

#### Scenario: Suggestions
- **WHEN** the user has the skill `PostgreSQL` and starts typing `Post` in a technologies field
- **THEN** `PostgreSQL` is offered as a suggestion

### Requirement: Projects section
The page SHALL show a Projects section with, for each project, fields for name, type (Assignment, Personal, Open source, Work), context, start and end month, description, technologies, results, links (a label and an address each, at most five) and a "Highlight" checkbox. The browser SHALL check the cheap rules when a field is left (name required, end not before start, link addresses `http` or `https`), and the server stays the authority.

#### Scenario: Add a project
- **WHEN** the user adds a project with a name and type and saves
- **THEN** the request holds that project with empty technologies, results and links

#### Scenario: Link rules
- **WHEN** the user leaves a link address that is not an `http` or `https` address
- **THEN** a message names that field, and if the profile is saved anyway the server's message is shown there

#### Scenario: Highlight
- **WHEN** the user ticks "Highlight" on a project and saves
- **THEN** the saved project has `highlight` true
