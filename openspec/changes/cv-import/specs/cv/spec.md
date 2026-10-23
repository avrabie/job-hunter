# Spec Delta

## Purpose
Defines how a user's single CV is stored, downloaded, replaced and deleted, and how an extraction of a profile draft from it is started.

## ADDED Requirements

### Requirement: One CV per user
The system SHALL store at most one CV per user, keyed by the `sub` of the caller's token, and SHALL scope every read, write and delete by that `sub`. No endpoint SHALL accept a user id. The CV SHALL be kept until the user deletes it.

#### Scenario: Upload and read the metadata
- **WHEN** a user uploads a valid PDF through `POST /api/cv` and then calls `GET /api/cv`
- **THEN** the answer holds the file name, content type, size in bytes and upload time, and no file content

#### Scenario: No CV yet
- **WHEN** a user without a CV calls `GET /api/cv`
- **THEN** the answer is 404

#### Scenario: Replace
- **WHEN** a user who has a CV uploads another valid file
- **THEN** only the new file is stored and the metadata describes it

#### Scenario: User A cannot reach user B's CV
- **WHEN** users A and B each uploaded a CV and A calls the CV endpoints
- **THEN** A sees, downloads, replaces and deletes only A's CV, and B's CV is unchanged

#### Scenario: Anonymous call
- **WHEN** any CV endpoint is called without a valid token
- **THEN** the answer is 401

### Requirement: Accepted files
The system SHALL accept only PDF, Markdown (`.md`) and plain text (`.txt`) files of at most 5 MB. The type SHALL be decided from the content, not from the declared content type: a PDF starts with `%PDF-`, and a text or Markdown file is valid UTF-8 without NUL bytes. A rejected upload SHALL change nothing and answer 400 (wrong type or unreadable) or 413 (too large) with a short fixed message that does not echo the file name or content.

#### Scenario: Each accepted kind
- **WHEN** a PDF, a `.md` file and a `.txt` file are uploaded in turn
- **THEN** each is accepted and the stored type is `application/pdf`, `text/markdown` or `text/plain` respectively

#### Scenario: Wrong content
- **WHEN** a file named `cv.pdf` that does not start with `%PDF-`, or a binary file named `cv.txt`, is uploaded
- **THEN** the answer is 400 and an earlier CV of that user is unchanged

#### Scenario: Too large
- **WHEN** a file over 5 MB is uploaded
- **THEN** the answer is 413 and an earlier CV is unchanged

#### Scenario: Name is display-only
- **WHEN** a file is uploaded with a name containing path separators or control characters
- **THEN** the stored display name has them removed and the name is never used as a path

### Requirement: Download and delete
`GET /api/cv/file` SHALL return the stored bytes of the caller's CV with `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`. `DELETE /api/cv` SHALL remove the caller's CV completely and answer 204, also when there is none.

#### Scenario: Download
- **WHEN** a user downloads their CV
- **THEN** the bytes equal what was uploaded and the response is an attachment

#### Scenario: Delete
- **WHEN** a user deletes their CV and then calls `GET /api/cv` and `GET /api/cv/file`
- **THEN** both answer 404

#### Scenario: Removed with the user
- **WHEN** a user's account row is deleted
- **THEN** their CV row is deleted with it

### Requirement: Start an extraction
`POST /api/cv/extract` SHALL read the text of the caller's stored CV and answer with a profile draft in the same shape as `GET /api/profile` content. It SHALL NOT store the draft or change the profile. It SHALL answer 404 when the user has no CV, 422 when no text can be read from the CV (for example a scanned PDF), 429 when the user exceeded the extraction limit, and 502 with a fixed message when the language model fails. Text longer than the configured limit SHALL be refused with 413 instead of being cut silently.

#### Scenario: Draft returned, profile untouched
- **WHEN** a user with a CV calls `POST /api/cv/extract`
- **THEN** the answer is 200 with a draft, and `GET /api/profile` still returns what it returned before

#### Scenario: No CV
- **WHEN** a user without a CV calls the endpoint
- **THEN** the answer is 404

#### Scenario: Scanned PDF
- **WHEN** the stored PDF contains no extractable text
- **THEN** the answer is 422 and the language model is not called

#### Scenario: Rate limit
- **WHEN** a user calls the endpoint more often than the configured limit per hour
- **THEN** the answer is 429 and the language model is not called

#### Scenario: Model failure
- **WHEN** the language model call fails
- **THEN** the answer is 502 with a fixed message that holds no CV text and no model output

### Requirement: No CV content in logs
The system SHALL NOT write CV bytes, CV text, file names, prompts or model answers to any log at INFO or above, nor to DEBUG of this application's own loggers. Log lines about CV work SHALL hold only outcomes, sizes and durations.

#### Scenario: Marker never logged
- **WHEN** a CV containing a unique marker is uploaded, extracted (with a fake model that echoes its input) and deleted
- **THEN** the marker appears in no captured log line
