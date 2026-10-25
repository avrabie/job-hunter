# file-cabinet Specification

## Purpose
Defines the per-user file cabinet: uploading, listing, downloading and deleting files of any kind, the CV files among them, and the master CV that the language-model features read.

## Requirements

### Requirement: Files belong to their owner
The system SHALL keep any number of files per user. Every read, write and delete SHALL be scoped by the `sub` of the caller's token. No endpoint SHALL accept a user id, and an id that belongs to another user SHALL behave exactly like an unknown id (404).

#### Scenario: Own files only
- **WHEN** users A and B each uploaded files and A lists, reads, downloads and deletes by id
- **THEN** A reaches only A's files, and A's request for one of B's ids answers 404

#### Scenario: Anonymous call
- **WHEN** any cabinet endpoint is called without a valid token
- **THEN** the answer is 401

### Requirement: Upload
`POST /api/files` (multipart, field `file`, optional field `kind` of `CV` or `OTHER`, default `OTHER`) SHALL store the file and answer 200 with its details: `id`, `name`, `kind`, `contentType`, `sizeBytes`, `uploadedAt`, `master`. The file SHALL be read as a stream and SHALL NOT be held in memory. A failed or aborted upload SHALL leave no file and no row. An unknown `kind` SHALL answer 400.

#### Scenario: Upload any file
- **WHEN** a user uploads a 3 MB file named `references.zip` with kind `OTHER`
- **THEN** the answer holds its details with `master` false, and the same bytes can be downloaded

#### Scenario: Several files, same name
- **WHEN** a user uploads two files with the same name
- **THEN** both are kept with different ids

#### Scenario: Aborted upload
- **WHEN** an upload fails halfway
- **THEN** no row exists for it and no partial file remains on disk

### Requirement: CV files
A file of kind `CV` SHALL be a PDF, a Markdown file (`.md`) or a plain text file (`.txt`) of at most 5 MB. The type SHALL be decided from the content, not from the declared content type: a PDF starts with `%PDF-`, a text or Markdown file is valid UTF-8 without NUL bytes, and the extension has to agree. A rejected CV upload SHALL answer 400 (wrong type) or 413 (over 5 MB) with a fixed message that does not echo the file name or content.

#### Scenario: Each accepted CV kind
- **WHEN** a PDF, a `.md` and a `.txt` file are uploaded with kind `CV`
- **THEN** each is accepted with content type `application/pdf`, `text/markdown` or `text/plain`

#### Scenario: Wrong content
- **WHEN** a file named `cv.pdf` that does not start with `%PDF-`, or a binary file named `cv.txt`, is uploaded with kind `CV`
- **THEN** the answer is 400 and nothing is stored

#### Scenario: Too large for a CV
- **WHEN** a 6 MB file is uploaded with kind `CV`
- **THEN** the answer is 413 and nothing is stored

### Requirement: Other files
A file of kind `OTHER` SHALL be accepted with any content and any name, up to 10 GiB, with the declared content type or `application/octet-stream` when none is given. Larger files SHALL answer 413. The stored display name SHALL have path separators and control characters removed and SHALL never be used as a path.

#### Scenario: Over the limit
- **WHEN** a file larger than 10 GiB is uploaded
- **THEN** the answer is 413 and nothing is stored

#### Scenario: Name is display-only
- **WHEN** a file is uploaded as `../../etc/passwd`
- **THEN** the stored name is `passwd` and the bytes are stored under a generated path

### Requirement: Disk protection
The system SHALL refuse an upload with status 507 and a fixed message when its declared size (`Content-Length`) would leave less than the configured free space (default 2 GiB) on the storage volume, and SHALL answer 507 and remove any partial data when writing fails because the disk is full. There SHALL be no per-user quota and no limit on the number of files.

#### Scenario: Disk nearly full
- **WHEN** free space is below the configured reserve and a user uploads a file
- **THEN** the answer is 507 and nothing is stored

#### Scenario: Disk fills during the upload
- **WHEN** writing the upload fails because the disk is full
- **THEN** the answer is 507, no row exists and no partial file remains

### Requirement: List and details
`GET /api/files` SHALL list the caller's files, newest first, with the details above and an optional `kind` filter. `GET /api/files/{id}` SHALL answer the details of one file.

#### Scenario: List
- **WHEN** a user with three files calls `GET /api/files`
- **THEN** the answer holds three entries, newest first

#### Scenario: Filter
- **WHEN** a user calls `GET /api/files?kind=CV`
- **THEN** only CV files are listed

### Requirement: Download
`GET /api/files/{id}/content` SHALL stream the stored bytes with `Content-Length`, `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff` and support for `Range` requests. The caller SHALL be authenticated either by the access token, or by a download ticket: `POST /api/files/{id}/download-ticket` SHALL answer a path with a random single-use ticket that is valid for 60 seconds, only for that file and that user, so a browser can start the download directly and stream it to disk.

#### Scenario: Download with the token
- **WHEN** an authenticated user requests their file's content
- **THEN** the bytes equal what was uploaded and the response is an attachment

#### Scenario: Range
- **WHEN** a client requests `Range: bytes=0-9`
- **THEN** the answer is 206 with the first ten bytes

#### Scenario: Ticket download
- **WHEN** a user obtains a ticket and a request without a token uses it within 60 seconds
- **THEN** the bytes are returned, and a second use of the same ticket answers 401

#### Scenario: Ticket is bound
- **WHEN** a ticket for one file is used for another file, after 60 seconds, or by an unknown value
- **THEN** the answer is 401 and nothing is returned

### Requirement: Delete
`DELETE /api/files/{id}` SHALL remove the row and then the bytes, answer 204 also when the id is unknown or not the caller's, and SHALL never leave a row without bytes. If the file was the master, the master choice SHALL be cleared.

#### Scenario: Delete
- **WHEN** a user deletes their file
- **THEN** it no longer appears in the list and its download answers 404

#### Scenario: Delete the master
- **WHEN** the master CV is deleted
- **THEN** the user has no master CV

### Requirement: Master CV
A user SHALL have at most one master CV, chosen explicitly: `PUT /api/files/master` with `{"fileId": "<id>"}` sets it and answers the file's details with `master` true, `DELETE /api/files/master` clears it, and `GET /api/files/master` answers the master file's details or 404. Only a file of kind `CV` that belongs to the caller can be the master (otherwise 404 for a missing file, 400 for a file of kind `OTHER`). A new upload SHALL NOT change the master.

#### Scenario: Choose a master
- **WHEN** a user with two CV files sets one as master and then the other
- **THEN** only the second has `master` true

#### Scenario: Other kind
- **WHEN** a user tries to set a file of kind `OTHER` as master
- **THEN** the answer is 400 and the master is unchanged

#### Scenario: Upload keeps the master
- **WHEN** a user with a master CV uploads another CV
- **THEN** the master is unchanged

### Requirement: Move of the old single CVs
On start, each CV stored by the earlier single-CV feature SHALL be moved into the cabinet as a file of kind `CV`, set as its owner's master unless the owner already has a master, and the old row SHALL be removed only after the new file and row exist. Running the move again SHALL change nothing.

#### Scenario: Existing CV moved
- **WHEN** the application starts with an old CV stored for a user
- **THEN** the user's list holds that file as master with the same bytes, and the old store holds nothing for them

#### Scenario: Master chosen before the move
- **WHEN** the owner already has a master CV when an old CV is moved
- **THEN** the old CV becomes a normal CV file and the chosen master is unchanged

#### Scenario: Second start
- **WHEN** the application starts again
- **THEN** nothing is moved or duplicated

### Requirement: Orphaned files
The system SHALL remove, once a day, stored byte files that no row refers to and that are older than 24 hours, and SHALL log only counts. Removing a user SHALL remove their rows, and their bytes SHALL be removed by this sweep.

#### Scenario: Orphan removed
- **WHEN** a byte file without a row is older than 24 hours when the sweep runs
- **THEN** it is deleted, and a file with a row or a younger orphan is kept

### Requirement: No file content in logs
The system SHALL NOT write file bytes, file names, download tickets or CV text to any log at INFO or above, nor to DEBUG of this application's own loggers.

#### Scenario: Markers never logged
- **WHEN** a file with a marker in its name and content is uploaded, listed, downloaded with a ticket and deleted
- **THEN** neither marker nor ticket appears in any captured log line

### Requirement: Read an own JPEG for another module
The files module SHALL offer other modules `FileCabinet.image(sub, id)`, which returns the user's own file `id` (details and path of its bytes) only when the file belongs to `sub`, and its first bytes are those of a JPEG (`FF D8 FF`). For a file that is missing, belongs to another user, is not a JPEG or is larger than 6 MB it SHALL return null, without saying which. The caller reads the bytes and never writes them. The method SHALL NOT log file names.

#### Scenario: Own JPEG
- **WHEN** a user uploaded a JPEG named `photo.jpg` and another module asks for it by id
- **THEN** it receives the details and a readable path

#### Scenario: Other users and other types
- **WHEN** the id belongs to another user, or the file is a PDF or a PNG named `x.jpg`
- **THEN** the result is null
