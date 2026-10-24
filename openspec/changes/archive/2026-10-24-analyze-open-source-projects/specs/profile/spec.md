## MODIFIED Requirements

### Requirement: Size limits
Name-like fields SHALL be at most 200 characters, the summary 4000 and each achievement 600. A profile SHALL have at most 30 experience entries, 20 achievements per entry, 20 education entries, 200 skills, 10 languages, 10 target roles and 30 cantons.

#### Scenario: Too many entries
- **WHEN** a profile has 31 experience entries
- **THEN** the response is 400 and nothing is stored

#### Scenario: Too long text
- **WHEN** a summary has 4001 characters
- **THEN** the response is 400 and nothing is stored

#### Scenario: Too many skills
- **WHEN** a profile has 201 skills
- **THEN** the response is 400 and nothing is stored

## ADDED Requirements

### Requirement: Skill origin and sources
A skill SHALL have a `name`, an optional `level`, an `origin` (`MANUAL` by default, or `ANALYSIS`) and a `sources` list (empty by default). A source SHALL have a `provider`, a `fullName` (`owner/repo`), an absolute `https` `url` (at most 500 characters) and an `evidence` text (not blank, at most 300 characters). A skill SHALL have at most 20 sources. A stored profile without `origin` and `sources` SHALL read as `MANUAL` with no sources. Violations SHALL be rejected with the field path (for example `skills[3].sources[0].url`) and a message that never repeats the submitted value.

#### Scenario: Round trip
- **WHEN** a skill with origin `ANALYSIS` and one source is saved and read back
- **THEN** it is returned unchanged

#### Scenario: Old skills
- **WHEN** a stored skill has only a name and a level
- **THEN** it reads with origin `MANUAL` and an empty `sources` list

#### Scenario: Bad source
- **WHEN** a source has the url `javascript:alert(1)` or a blank evidence
- **THEN** the response is 400 and `fields` contains the path of that field

#### Scenario: Too many sources
- **WHEN** a skill has 21 sources
- **THEN** the response is 400 and nothing is stored

### Requirement: Stored project analysis
An open source project MAY have an `analysis` (null by default) with `summary` (at most 1500 characters), `architecture` (at most 2500), `techStack` (at most 20 entries, each `name` at most 60, `category` one of `LANGUAGE`, `FRAMEWORK`, `LIBRARY`, `DATABASE`, `TOOL`, `PLATFORM`, `OTHER`, `evidencePath` at most 300, `evidence` at most 300), `files` (at most 9 paths of at most 300 characters), `truncated` and `analyzedAt`. A stored profile without it SHALL read with `analysis` null. Violations SHALL be rejected like other profile errors.

#### Scenario: Round trip
- **WHEN** a project with a full analysis is saved and read back
- **THEN** the analysis is returned unchanged

#### Scenario: Old projects
- **WHEN** a stored project has no `analysis`
- **THEN** it reads with `analysis` null

#### Scenario: Too many technologies
- **WHEN** an analysis lists 21 technologies, or a summary has 1501 characters
- **THEN** the response is 400 and nothing is stored
