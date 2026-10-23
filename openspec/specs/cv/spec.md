# cv Specification

## Purpose
Defines how a user's single CV is stored, downloaded, replaced and deleted, and how an extraction of a profile draft from it is started.

## Requirements

### Requirement: Start an extraction
`POST /api/cv/extract` SHALL read the text of the caller's master CV and answer with a profile draft in the same shape as `GET /api/profile` content. It SHALL NOT store the draft or change the profile. It SHALL answer 409 with a fixed message when the user has not chosen a master CV, 422 when no text can be read from the CV (for example a scanned PDF), 429 when the user exceeded the extraction limit, and 502 with a fixed message when the language model fails. Text longer than the configured limit SHALL be refused with 413 instead of being cut silently.

#### Scenario: Draft returned, profile untouched
- **WHEN** a user with a master CV calls `POST /api/cv/extract`
- **THEN** the answer is 200 with a draft, and `GET /api/profile` still returns what it returned before

#### Scenario: Reads the master, not another CV
- **WHEN** a user has two CV files and the second is the master
- **THEN** the text sent to the model is the second file's text

#### Scenario: No CV
- **WHEN** a user without a master CV calls the endpoint, whether they have no files at all or only CV files that are not the master
- **THEN** the answer is 409 and the language model is not called

#### Scenario: Scanned PDF
- **WHEN** the master PDF contains no extractable text
- **THEN** the answer is 422 and the language model is not called

#### Scenario: Rate limit
- **WHEN** a user calls the endpoint more often than the configured limit per hour
- **THEN** the answer is 429 and the language model is not called

#### Scenario: Model not set up
- **WHEN** no model key is configured and a user requests an extraction
- **THEN** the answer is 503 with a fixed message

#### Scenario: Model failure
- **WHEN** the language model call fails
- **THEN** the answer is 502 with a fixed message that holds no CV text and no model output

### Requirement: No CV content in logs
The system SHALL NOT write CV bytes, CV text, file names, prompts or model answers to any log at INFO or above, nor to DEBUG of this application's own loggers. Log lines about CV work SHALL hold only outcomes, sizes and durations.

#### Scenario: Marker never logged
- **WHEN** a CV containing a unique marker is uploaded, extracted (with a fake model that echoes its input) and deleted
- **THEN** the marker appears in no captured log line

### Requirement: Extract a single section
`POST /api/cv/extract?section=<name>` with `name` one of `experience`, `education`, `publications` or `projects` SHALL read the master CV like the whole-profile extraction and answer with a profile in the same shape in which only the requested section is filled and every other list is empty and every other optional field is null. An unknown section SHALL answer 400. The master requirement, the text limit, the hourly limit and the error answers (409, 413, 422, 429, 502, 503) SHALL be the same as for the whole profile, and a section request counts as one extraction for the limit.

#### Scenario: One section returned
- **WHEN** a user with a master CV requests `section=education`
- **THEN** the answer is 200 and holds the education entries, with empty experience, publications and skills

#### Scenario: Projects section
- **WHEN** a user with a master CV requests `section=projects`
- **THEN** the answer is 200 and holds the projects, with empty experience, education and publications

#### Scenario: Unknown section
- **WHEN** a user requests `section=hobbies`
- **THEN** the answer is 400 and the language model is not called

#### Scenario: No master
- **WHEN** a user without a master CV requests a section
- **THEN** the answer is 409 and the language model is not called

#### Scenario: Profile untouched
- **WHEN** a section is extracted
- **THEN** `GET /api/profile` still returns what it returned before
