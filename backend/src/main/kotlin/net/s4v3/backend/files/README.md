# files

Purpose: the user's file cabinet. Any number of files per user; bytes on the files volume, metadata in Postgres; at most one master CV; single-use download tickets.

## Public API (this package)
- `FileCabinet.masterCv(sub)` returns a `CabinetFile` (id, name, content type, size, SHA-256 hex, `Path` of the bytes) or null. Callers read the file, never write it.
- `FileCabinet.image(sub, id)` returns the same for the user's own file when it is a JPEG (`FF D8 FF`) of at most 6 MB, else null (no reason given).

## Internal (`internal/`)
- `FilesController`: `/api/files` (upload, list, details, master get/put/delete, download ticket, content, delete).
- `JdbcFileRepository`, `FileCabinetService`.
- `FileStorage`: the only class that touches the volume; keys are generated, never from a name.
- `CvContent`: CV type decided from the content (PDF, Markdown, text, 5 MB).
- `DownloadTickets`: in-memory, 32 random bytes, one use, 60 s.
- `FileUploadGuards.kt`: `FreeSpaceGuard` (507), `FilesUploadLimitResolver` (413).
- `OrphanSweep` (daily 03:30), `LegacyCvMover` (startup), `FilesSettings`.

## Events
- None.

## Tables
- `user_file` (`db/changelog/008-files.sql`); reads and drains the legacy `cv_document` (`007-cv.sql`).

## Settings
- `jobhunter.files.*` (`dir`, `min-free-bytes`, `max-other-bytes`, `max-cv-bytes`, `ticket-seconds`); multipart limits in `application.yaml`.

## Calls
- `accounts`.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `FilesApiTests`, `FileCabinetTests`, `FileCabinetImageTests`, `FileRepositoryTests`, `FilesSchemaTests`, `FilesLoggingTests`, `OrphanSweepTests`, `LegacyCvMoverTests`.
- Unit: `FileStorageTests`, `CvContentTests`.

Read on 2026-10-24 from: files/FileCabinet.kt, files/internal/*.kt (FilesController, FileStorage, DownloadTickets, OrphanSweep, FileUploadGuards, CvContent, FilesSettings), db/changelog/007-008, test file names.
