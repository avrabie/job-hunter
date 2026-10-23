# Spec Delta

## MODIFIED Requirements

### Requirement: Profile content
The profile SHALL be language-neutral and SHALL contain `location`, `workAuthorisation`, `languages`, `summary`, `experience`, `education`, `publications`, `skills` and `preferences` (target roles, workload range, remote modes, cantons, minimum yearly salary in CHF). Experience and education entries carry `YYYY-MM` months; an entry without an end month is current. `publications` is a list, empty by default.

#### Scenario: Round trip keeps every field
- **WHEN** a profile that uses every field is saved and read back
- **THEN** the returned content equals what was saved, with entries in the same order

#### Scenario: Current position
- **WHEN** an experience entry has no end month
- **THEN** it is accepted and returned with a null end month

#### Scenario: Profile saved before publications existed
- **WHEN** a stored profile document has no `publications` field
- **THEN** `GET /api/profile` returns it with an empty `publications` list

## ADDED Requirements

### Requirement: Publications
A publication SHALL have a required `title` and the optional fields `venue` (journal, conference or publisher), `year`, `authors` (free text) and `url`. `year` SHALL be a whole number from 1900 to the current year plus one. `url` SHALL be an `http` or `https` address. Text limits: `title` and `venue` at most 200 characters, `authors` at most 500, `url` at most 500. A profile SHALL have at most 100 publications. Violations SHALL be rejected like other profile errors, with the field path (for example `publications[0].year`) and a message that never repeats the submitted value.

#### Scenario: Valid publication
- **WHEN** a profile with a publication that has a title, venue, year 2021, authors and an https link is saved and read back
- **THEN** the publication is returned unchanged

#### Scenario: Title only
- **WHEN** a publication has only a title
- **THEN** it is accepted and the other fields are null

#### Scenario: Blank title
- **WHEN** a publication has a blank title
- **THEN** the response is 400 and `fields` contains `publications[0].title`

#### Scenario: Year out of range
- **WHEN** a publication has year 1850, or a year more than one above the current year
- **THEN** the response is 400 and `fields` contains `publications[0].year`

#### Scenario: Unsafe link
- **WHEN** a publication's `url` is `javascript:alert(1)` or a relative path
- **THEN** the response is 400 and `fields` contains `publications[0].url`

#### Scenario: Too many publications
- **WHEN** a profile has 101 publications
- **THEN** the response is 400 and nothing is stored
