## MODIFIED Requirements

### Requirement: Section extraction
The module SHALL offer a typed extraction of one profile section (experience, education or publications) from CV text. The prompt SHALL ask for every entry of that section the CV states, with all stated details (for experience every achievement and technology, not a summary), and SHALL keep the rules of the whole-profile extraction: only stated facts, no guessing, CV text as quoted data, no tools. The same value checks as for the whole profile apply: an invalid optional value becomes null and an entry that lacks a required value is left out.

#### Scenario: Only the requested section
- **WHEN** the model returns a valid answer for the experience section
- **THEN** the result is a profile whose experience entries come from the answer and whose other sections are empty

#### Scenario: Complete entries
- **WHEN** the answer has a position with six achievements
- **THEN** all six are in the result in order

#### Scenario: Bad values
- **WHEN** an entry has an invalid start month
- **THEN** that entry is left out and the others are kept

#### Scenario: Unparseable answer
- **WHEN** the answer cannot be parsed after the repair attempts
- **THEN** the call fails with the same fixed error as the whole-profile extraction

#### Scenario: Projects section
- **WHEN** the model's answer for any section holds a `projects` list
- **THEN** it is ignored and the result holds no projects

## REMOVED Requirements

### Requirement: Technologies and projects in the draft
**Reason**: Projects leave the profile; technologies per position are kept by the new requirement below.
**Migration**: None.

## ADDED Requirements

### Requirement: Technologies in the draft
The typed CV draft SHALL include, for each position, the technologies the CV names for it, and SHALL NOT include projects. Technology tags SHALL be trimmed, blank tags dropped and duplicates that differ only in case removed (the first spelling is kept), at most 30 per entry.

#### Scenario: Technologies on a position
- **WHEN** the model returns a position with technologies `["Kotlin", "kotlin", " Spring Boot "]`
- **THEN** the position holds `["Kotlin", "Spring Boot"]`

#### Scenario: Projects in the answer are ignored
- **WHEN** the model's answer contains a `projects` list
- **THEN** it does not appear in the resulting profile
