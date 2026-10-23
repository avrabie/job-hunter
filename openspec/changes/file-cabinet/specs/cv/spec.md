# Spec Delta

## REMOVED Requirements

### Requirement: One CV per user
**Reason**: Users can keep several files; storage and ownership rules are now in `file-cabinet`.
**Migration**: Use `POST /api/files` with kind `CV`; stored CVs are moved by the one-time migration.

### Requirement: Accepted files
**Reason**: The CV file rules (PDF, Markdown, plain text, 5 MB, content checks) are now part of `file-cabinet`.
**Migration**: Same rules apply to files of kind `CV`.

### Requirement: Download and delete
**Reason**: Replaced by `GET /api/files/{id}/content` and `DELETE /api/files/{id}` in `file-cabinet`.
**Migration**: Use the cabinet endpoints.

## MODIFIED Requirements

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
