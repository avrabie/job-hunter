# Design

## Context

Today `cv` stores one file per user as `bytea` in `cv_document` (changeset 010), reads it fully into memory and checks type and size in `CvContent`. Ten gigabytes per file cannot work that way (PostgreSQL limits a `bytea` value to 1 GB, and the JVM and the pod have 1 GiB of memory). The backend is a single replica with a `Recreate` strategy and the cluster uses `local-path` volumes (no backup yet). The gateway proxies `/api` with Spring Cloud Gateway; the edge is Traefik behind a Gateway API route. Tokens live 5 to 60 minutes, and the access token is checked when a request starts.

## Goals / Non-Goals

**Goals:** any number of files per user, up to 10 GiB each, streamed in and out; CV files readable by the model; an explicit master CV; no data lost in the move from the old store.

**Non-Goals:** per-user quotas, folders, rename, sharing links, virus scanning, thumbnails, resumable (chunked) uploads, encryption, backups (a separate infrastructure change).

## Decisions

- **Bytes on a volume, metadata in Postgres.** A `PersistentVolumeClaim` `backend-files` (default 50 Gi, `local-path`) is mounted at `/data/files`. Each file is stored at `<dir>/<first two hex of id>/<id>` with a generated UUID; the user's file name is only a database column. Alternative considered: S3 (Garage/MinIO). It adds a service to run for a handful of friends, and the one-pod, one-volume design is enough. The `FileStorage` class is the only code that touches the disk, so an S3 version can replace it later.
- **Upload path.** Spring's multipart handling (Tomcat) already streams a part to a temporary file, so `multipart.location` is set to `<dir>/tmp` on the same volume and the controller moves the temp file into place with `MultipartFile.transferTo(Path)` (a rename on one volume, no second copy). The SHA-256 is computed while reading the temporary file once. Limits: `max-file-size` and `max-request-size` of 10 GB at the server; the service enforces 5 MB for kind `CV` and 10 GiB for `OTHER` again. Alternative considered: Commons FileUpload streaming API, which can abort mid-stream; rejected as too intricate for the gain.
- **Disk guard.** A servlet filter on `POST /api/files` compares `Content-Length` with `File.getUsableSpace() - minFreeBytes` and answers 507 before any byte is read; writing failures with "No space left" are mapped to 507, with the temporary file removed.
- **Atomic and ordered.** Move the temp file into place, then insert the row; if the insert fails, delete the file. Delete removes the row first, then the file; a crash between them leaves an orphan, which the daily sweep removes (files with no row older than 24 h). That also covers rows removed by `ON DELETE CASCADE` when a user is deleted.
- **Schema (changeset 011, file `008-files.sql`).** `user_file(id uuid pk, owner_sub text fk app_user on delete cascade, name text, kind text check in ('CV','OTHER'), content_type text, size_bytes bigint check (size_bytes between 0 and 10737418240), sha256 text, storage_key text unique, master boolean default false, uploaded_at timestamptz)`, a partial unique index on `(owner_sub) where master`, a check that only `CV` files can be master, and an index on `(owner_sub, uploaded_at desc)`. The master is a flag, so setting a new master is one transaction: clear, then set.
- **Download.** `GET /api/files/{id}/content` returns a `FileSystemResource`, which gives `Content-Length` and `Range` handling in Spring MVC with no buffering. Because a browser cannot send a bearer token on a plain navigation, and fetching 10 GB into a Blob would exhaust the tab, the UI first calls `POST /api/files/{id}/download-ticket` (authenticated) and then navigates to the returned path. A ticket is 32 random bytes (hex), kept in memory with user, file and expiry (60 s), removed on first use. The content endpoint is allowed without a token only when a valid ticket is present (checked in a filter before the controller); the bearer path stays as is. The ticket is never logged. Tickets are lost on a restart, which is fine for 60-second values.
- **Module layout.** A new module `files` (public: `FileCabinet` with `masterCv(sub): CabinetFile?` where `CabinetFile` holds id, name, content type, size and a `Path`; everything else `internal`). `cv` depends on `files` for extraction and loses its storage code. `CvContent` (content checks, name sanitising) moves to `files.internal`. `ModularityTests` lists eleven modules.
- **Extraction input.** `POST /api/cv/extract` takes the master file's path from `FileCabinet`; it reads at most 5 MB of bytes (kind `CV` guarantees that).
- **Move of old CVs.** A startup runner (`LegacyCvMover`, `ApplicationRunner`) copies each `cv_document` row to a file and a `user_file` row (kind `CV`, master true), then deletes the old row, one user per transaction; it is idempotent. A later change drops the empty `cv_document` table.
- **UI.** `fetch` cannot report upload progress, so upload uses `XMLHttpRequest` (progress event, abort). Download is a ticket request followed by `window.location.assign` of the returned path (the response is an attachment, so the page stays). The list loads once and is updated from server answers.
- **Operations to check on the real cluster before promising 10 GB:** Traefik entrypoint `readTimeout`, the gateway's Reactor Netty timeouts and any body-size limit, Tomcat `connection-timeout`/`keep-alive`, and the disk size of the node. The practical limit may be lower until these are raised; this is a spike task with a measured result, not an assumption.

## Risks / Trade-offs

- A full disk stops uploads (guard) but also database writes if both share the node disk; the 2 GiB reserve protects Postgres, and a larger reserve is a setting.
- No backup of `local-path` volumes: uploaded files can be lost with the node. Already true for the database.
- Without quotas one friend can fill the disk. Accepted by the owner; `min-free-bytes` is the safety net.
- A 10 GB upload over a slow link takes a long time and a token cannot be renewed mid-request; this is fine because the token is checked only when the request starts, but a flaky connection means starting over (no resume in this change).
- Memory: Tomcat's temp file approach keeps memory flat; the old `bytea` path is gone.
