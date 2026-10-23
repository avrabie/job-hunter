# Spec Delta

## MODIFIED Requirements

### Requirement: Section extraction
The module SHALL offer a typed extraction of one profile section (experience, education, publications or projects) from CV text. The prompt SHALL ask for every entry of that section the CV states, with all stated details (for experience every achievement and technology, not a summary), and SHALL keep the rules of the whole-profile extraction: only stated facts, no guessing, CV text as quoted data, no tools. The same value checks as for the whole profile apply: an invalid optional value becomes null and an entry that lacks a required value is left out.

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
- **WHEN** the model returns a valid answer for the projects section
- **THEN** the result is a profile whose projects come from the answer and whose other sections are empty

## ADDED Requirements

### Requirement: Technologies and projects in the draft
The typed CV draft SHALL include, for each position, the technologies the CV names for it, and a list of projects (assignments, personal and open-source projects) with name, type, context, period, description, technologies, results and links, each only as far as the CV states it. Technology tags SHALL be trimmed, blank tags dropped and duplicates that differ only in case removed (the first spelling is kept), at most 30 per entry. A project type that is not one of the four allowed values SHALL become `PERSONAL`; a project without a name SHALL be left out; a link that is not an `http` or `https` address, or has no label, SHALL be left out of its project. The model SHALL NOT set `highlight`.

#### Scenario: Technologies on a position
- **WHEN** the model returns a position with technologies `["Kotlin", "kotlin", " Spring Boot "]`
- **THEN** the position holds `["Kotlin", "Spring Boot"]`

#### Scenario: Project read
- **WHEN** the model returns a project with a name, type `OPEN_SOURCE`, two results and a GitHub link
- **THEN** the draft holds it with those values and `highlight` false

#### Scenario: Unknown type
- **WHEN** a project has the type "hobby"
- **THEN** the draft holds it with type `PERSONAL`

#### Scenario: Bad link and nameless project
- **WHEN** a project has a link `ftp://example.org/x` and another project has no name
- **THEN** the link is dropped from its project and the nameless project is left out
