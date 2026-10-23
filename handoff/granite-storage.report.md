# Report: granite-storage

## Short answer

granite-security keeps file bytes in S3-compatible object storage (Garage, one public bucket) and file metadata in Postgres
(`user_file` rows keyed by `username`). Browsers upload directly through presigned PUT URLs. There is no encryption at rest
or per-field, no virus scanning, no magic-byte sniffing and no export; its only controls are a content-type allow-list,
count/size caps, a unique content hash and an admin audit table. Hard delete is a saga: rows go in one local transaction,
then objects are removed best-effort. For Job Hunter this is the wrong shape for a CV (public bucket, no encryption, no
content check). Reuse its ideas (metadata row plus owner scoping, allow-list, caps, ordered delete, audit), not its storage.

## Findings (paths under /Users/moldo/code/adrian/granite-security)

1. Storage, limits, content types
- Bytes live in Garage (S3 API), run by a separate `storage` service that only signs URLs and deletes objects:
  `storage/src/main/java/org/granitesecurity/storage/service/StorageService.java`. Compose/k8s: `compose.yaml`,
  `k8s/base/garage.yaml`; plan in `docs/plans/storage.md`.
- Flow: browser calls `POST /api/storage/presign` (scope, contentType, fileName), PUTs straight to Garage, then calls profile
  to register the key. Key shape `user-files/<username>/<uuid>/<sanitized-name>`; filename sanitised to `[a-zA-Z0-9._-]`,
  basename only (StorageService `sanitize`, `sanitizeSegment`).
- Content type is the client-declared string checked against a per-scope allow-list (`ALLOWED_CONTENT_TYPES_BY_SCOPE`; user
  files: jpeg/png/webp/pdf/text/mp4/webm). Presigned URL is signed with that Content-Type, so S3 rejects a mismatching PUT,
  but the file content itself is never inspected.
- Limits are enforced only at register time and are advisory (object is already uploaded):
  `profile/.../service/UserFileService.java`: 5 GB max (S3 single-PUT limit), 500 MB for video, 50 files per user. Real
  ceiling is a Garage bucket quota (`docs/users/user-profile.md` ~L219, `docs/todo/guardrails.md` ~L51). Presign expiry 2 h.
- The bucket is public-read via Garage `s3_web`; the username is visible in file URLs (comment in StorageService says this is
  accepted). Avatars and product media use the same bucket, separate scopes (`docs/users/user-pic.md` D2).
- Metadata: `profile/src/main/resources/db/changelog/003-user-cabinet.sql` (`user_file`: id, username, file_name,
  object_key UNIQUE, url, content_type, size_bytes, created_at), `004-user-file-content-hash.sql` (client-computed SHA-256,
  unique on (username, content_hash), nullable for big files), `011-user-file-shared.sql` (public-profile flag).
  Entity: `profile/.../domain/UserFile.java`. Postgres blobs and filesystem are not used.

2. Per-user model, isolation, encryption
- Isolation is by `username` column on every row; services take the username from the JWT and queries use
  `findByIdAndUsername` etc. (`UserFileService`). Objects are isolated only by key prefix; the bucket is public, so anyone
  with a URL reads the file. Scope check: self-service scopes for any user, others ADMIN/MANAGER
  (`StorageService.requireScopeAllowedForCaller`).
- Encryption: none found. Grep for encrypt/pgcrypto/KMS in `profile`, `storage`, `docs` returned nothing relevant. No
  per-field encryption, no key handling, no SSE configured.

3. Retention, deletion, export, audit
- No retention policy and no export endpoint found.
- User delete: `profile/.../service/AdminUserService.java` (`delete`, `hardDelete`, `deleteProfileData`): block first, check
  eligibility (users with paid orders are blocked instead of deleted), purge other services, then in ONE local transaction
  delete address, file and profile rows, and only afterwards delete objects best-effort (`deleteObjectsQuietly`). A storage
  failure leaves orphan objects that "the quota tolerates and an admin can sweep". Single-file delete removes the object
  first, then the row (`UserFileService.delete`), mapping failures to 502.
- Orphan detection is read-only across DBs: `profile/.../service/OrphanSweepService.java`. No object-vs-row sweep yet
  (listed as future work in `docs/plans/storage.md` ~L409). Backup of the single-node Garage PVC is also only a plan.
- Audit: `admin_action` table (actor, action BLOCK/UNBLOCK/DELETE, target_user, outcome, reason, created_at),
  `profile/.../changelog/005-admin-action.sql`. Admin actions only, not user file access.

4. Upload validation
- Only the allow-list, size/count caps and filename sanitising above. No virus scanning (explicitly "probably overkill",
  `docs/plans/storage.md` ~L415), no magic-byte check, no rate limiting on presign.

## Comparison with Job Hunter

- Job Hunter `profile` is one JSONB document per user, `owner_sub` PK with `ON DELETE CASCADE` to `app_user`, optimistic
  `version`, `schema_version` (`db/changelog/002-profile.sql`), body capped at 128 KB (`ProfileService.MAX_BODY_BYTES`).
  Ownership by token `sub` is stronger than granite's mutable `username`, and CASCADE gives atomic hard delete of DB data
  that granite has to hand-code.
- Granite needs object storage because of video up to 500 MB and a public gallery. A CV is a few hundred KB.

## Recommendation for Job Hunter

Storage: keep the CV in Postgres (bytea) in a separate table, not in the profile JSONB and not in object storage. A new
Garage/S3 service, a public bucket and a cross-store delete saga buy nothing at this size, and a Postgres row gets
transactional delete, backups and CASCADE for free. Revisit object storage only if files grow beyond a few MB or multiple
files per user are wanted.

Schema sketch (new changeset 007):
```
cv_document(
  owner_sub     TEXT PRIMARY KEY REFERENCES app_user(sub) ON DELETE CASCADE,  -- one CV per user to start
  file_name     TEXT NOT NULL,          -- sanitised display name only, never used as a path
  content_type  TEXT NOT NULL,          -- detected, not declared
  size_bytes    INT  NOT NULL CHECK (size_bytes <= 5242880),
  sha256        TEXT NOT NULL,          -- computed server-side
  content       BYTEA NOT NULL,         -- or encrypted blob, see below
  uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT now()
)
```
The extracted profile stays in the existing `profile.document` (it is already one JSON document per user); keep an
`source_cv_sha256` inside the document or a column so a re-extract can be skipped when unchanged.

Upload handling: multipart through the backend (not presigned, nothing is public). Enforce size at the stream (reject over
5 MB before buffering fully), allow PDF and DOCX only, verify magic bytes (`%PDF-`, ZIP signature plus `word/document.xml`)
rather than trusting Content-Type, sanitise the name, always serve with `Content-Disposition: attachment`, `nosniff`, and
only to the owner (`WHERE owner_sub = :sub`). Parse extraction in a bounded, time-limited step; a ClamAV sidecar is
optional and probably not worth it for single-owner private files that are only read back by the owner.

Deletion: `DELETE /cv` hard-deletes the row; user deletion removes it via CASCADE in the same transaction as the profile.
No second store, so no orphan sweep. Granite's ordering rule (DB first, object second) is only needed if S3 is adopted.

Encryption: granite has none to copy. Options, cheapest first: (a) rely on disk/volume encryption and encrypted backups
and state that plainly; (b) application-level AES-GCM of `content` (and optionally the profile document) with a key from a
Kubernetes Secret, a key id column for rotation, and `owner_sub` as AAD. (b) protects against a DB dump but not against a
compromised backend, and complicates search over profile fields, so apply it to the CV blob only.

Reuse from granite: allow-list plus size/count caps; filename sanitising; SHA-256 for duplicate detection (server-side);
owner scoping in every query; an audit table for admin delete (small `admin_action`-style table if an admin delete is
added); delete ordering; keeping validation at register time as a backstop.
Avoid: public bucket and public URLs for personal documents; username in URLs; trusting declared Content-Type; advisory-only
size limits after the upload already happened; client-computed hashes as a security control; best-effort orphaning of
personal data after a delete.

## Open questions for the user

1. One CV per user, or a history of versions? (Changes the PK and adds a retention rule.)
2. Is DB-volume encryption plus encrypted backups acceptable, or do you want application-level encryption of the CV now?
3. Which formats must be accepted (PDF only, or DOCX and others)? Scanned PDFs need OCR, which is a different feature.
4. Should the original CV be kept after extraction, or deleted once the profile is built (best privacy, no storage at all)?
5. Do you want a user-facing export (download the CV and profile JSON) alongside delete? Granite has neither.
