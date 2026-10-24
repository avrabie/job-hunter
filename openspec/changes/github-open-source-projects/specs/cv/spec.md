## MODIFIED Requirements

### Requirement: Extract a single section
`POST /api/cv/extract?section=<name>` with `name` one of `experience`, `education` or `publications` SHALL read the master CV like the whole-profile extraction and answer with a profile in the same shape in which only the requested section is filled and every other list is empty and every other optional field is null. An unknown section, including `projects`, SHALL answer 400. The master requirement, the text limit, the hourly limit and the error answers (409, 413, 422, 429, 502, 503) SHALL be the same as for the whole profile, and a section request counts as one extraction for the limit.

#### Scenario: One section returned
- **WHEN** a user with a master CV requests `section=education`
- **THEN** the answer is 200 and holds the education entries, with empty experience, publications and skills

#### Scenario: Unknown section
- **WHEN** a user requests `section=hobbies`
- **THEN** the answer is 400 and the language model is not called

#### Scenario: Projects section
- **WHEN** a user requests `section=projects`
- **THEN** the answer is 400 and the language model is not called

#### Scenario: No master
- **WHEN** a user without a master CV requests a section
- **THEN** the answer is 409 and the language model is not called

#### Scenario: Profile untouched
- **WHEN** a section is extracted
- **THEN** `GET /api/profile` still returns what it returned before
