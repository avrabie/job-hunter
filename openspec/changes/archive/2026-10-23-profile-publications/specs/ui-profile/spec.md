# Spec Delta

## MODIFIED Requirements

### Requirement: Editing lists
The page SHALL let the user add, remove and reorder experience, education, publications, skills, languages and achievements, and SHALL send the whole profile on save.

#### Scenario: Add and remove
- **WHEN** the user adds an experience entry, fills it and removes another
- **THEN** the saved profile contains the added entry and not the removed one

## ADDED Requirements

### Requirement: Publications section
The page SHALL show a Publications section with, for each entry, fields for title, venue, year, authors and link, and SHALL check them with the same rules as the server (title required, year range, http or https link) when a field is left. Empty optional fields SHALL be sent as null.

#### Scenario: Add a publication
- **WHEN** the user adds a publication with a title and year and saves
- **THEN** the request holds that publication with null venue, authors and link

#### Scenario: Bad link
- **WHEN** the user leaves the link field with `javascript:alert(1)`
- **THEN** a message names the link field, and if the profile is saved anyway the server's 400 message is shown at that field (the server stays the authority)

#### Scenario: Reorder and remove
- **WHEN** the user moves the second publication up and removes the third
- **THEN** the saved list reflects both changes
