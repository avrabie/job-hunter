# Spec Delta

## MODIFIED Requirements

### Requirement: Profile content
The profile SHALL be language-neutral and SHALL contain `location`, `workAuthorisation`, `languages`, `summary`, `experience`, `education`, `publications`, `projects`, `skills` and `preferences` (target roles, workload range, remote modes, cantons, minimum yearly salary in CHF). Experience and education entries carry `YYYY-MM` months; an entry without an end month is current. `publications` and `projects` are lists, empty by default, and every experience entry has a `technologies` list, empty by default.

#### Scenario: Round trip keeps every field
- **WHEN** a profile that uses every field is saved and read back
- **THEN** the returned content equals what was saved, with entries in the same order

#### Scenario: Current position
- **WHEN** an experience entry has no end month
- **THEN** it is accepted and returned with a null end month

#### Scenario: Profile saved before publications existed
- **WHEN** a stored profile document has no `publications` field
- **THEN** `GET /api/profile` returns it with an empty `publications` list

#### Scenario: Profile saved before projects and technologies existed
- **WHEN** a stored profile document has no `projects` field and its experience entries have no `technologies`
- **THEN** `GET /api/profile` returns it with an empty `projects` list and empty `technologies` on every entry

### Requirement: Safe error messages and body size
Validation messages SHALL describe the rule and SHALL NOT contain the submitted value. A request body larger than 256 KB SHALL be rejected with 413 before it is parsed into a profile.

#### Scenario: Value not echoed
- **WHEN** a too-long summary made of a recognisable marker text is rejected
- **THEN** the response body does not contain the marker text

#### Scenario: Oversized body
- **WHEN** the request body is larger than 256 KB
- **THEN** the response is 413

#### Scenario: Body between the old and the new limit
- **WHEN** a valid profile whose request body is 200 KB is saved
- **THEN** it is accepted and read back unchanged

## ADDED Requirements

### Requirement: Technologies
An experience entry and a project SHALL each have a `technologies` list of short tags. A tag SHALL not be blank and SHALL be at most 60 characters, and a list SHALL have at most 30 tags. The server SHALL accept any casing and SHALL NOT change the tags; tidying (trimming and removing duplicates that differ only in case) is the client's and the CV reader's job.

#### Scenario: Tags kept as sent
- **WHEN** an experience entry with technologies `["Kotlin", "Spring Boot"]` is saved and read back
- **THEN** the same two tags are returned in the same order

#### Scenario: Blank or long tag
- **WHEN** a tag is blank, or has 61 characters
- **THEN** the response is 400 and `fields` contains the path of that tag, for example `experience[0].technologies[1]`

#### Scenario: Too many tags
- **WHEN** an entry has 31 technologies
- **THEN** the response is 400 and nothing is stored

### Requirement: Projects
A project SHALL have a required `name` (at most 200 characters) and a required `type` of `ASSIGNMENT`, `PERSONAL`, `OPEN_SOURCE` or `WORK`, and the optional fields `context` (at most 200 characters), `start` and `end` (`YYYY-MM`, start not after end), `description` (at most 2000 characters), `technologies`, `results` (each line at most 600 characters, at most 20), `links` and `highlight` (default false). A link SHALL have a `label` (not blank, at most 60 characters) and an absolute `http` or `https` `url` (at most 500 characters), and a project SHALL have at most 5 links. A profile SHALL have at most 50 projects. Violations SHALL be rejected like other profile errors, with the field path and a message that never repeats the submitted value.

#### Scenario: Valid project
- **WHEN** a project with a name, type `ASSIGNMENT`, context, period, description, technologies, results and a repository link is saved and read back
- **THEN** it is returned unchanged

#### Scenario: Name and type only
- **WHEN** a project has only a name and a type
- **THEN** it is accepted and the other fields are empty, null or false

#### Scenario: Unknown type
- **WHEN** a project has type `HOBBY`
- **THEN** the response is 400 and nothing is stored

#### Scenario: Blank name
- **WHEN** a project has a blank name
- **THEN** the response is 400 and `fields` contains `projects[0].name`

#### Scenario: Dates out of order
- **WHEN** a project starts after it ends
- **THEN** the response is 400 and names `projects[0].end`

#### Scenario: Unsafe link
- **WHEN** a project link has the address `javascript:alert(1)`
- **THEN** the response is 400 and `fields` contains `projects[0].links[0].url`

#### Scenario: Too many
- **WHEN** a profile has 51 projects, or a project has 6 links
- **THEN** the response is 400 and nothing is stored
