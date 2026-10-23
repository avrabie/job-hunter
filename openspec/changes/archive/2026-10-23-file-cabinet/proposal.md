# Proposal

## Why

A user has one CV slot today. People keep several CVs (languages, roles) and other documents (certificates, letters, references) and want to use them from the site. The owner also wants friends to upload and download any file, up to 10 GB each, with no count limit. Files of that size cannot live in database rows, so storage moves to the server's disk.

## What Changes

- New `files` module, "the cabinet": a user uploads any number of files, lists, downloads and deletes them. Each file has a kind: `CV` (PDF, Markdown or plain text, at most 5 MB, readable by the language model) or `OTHER` (any content, any name, at most 10 GB).
- The user picks one CV file as the **master CV** (explicitly; there is no automatic choice). The "Fill the profile from my CV" button reads the master CV.
- File bytes are stored on a persistent volume in the backend pod, written and read as streams; metadata stays in Postgres. Uploads are refused when the disk is nearly full.
- Large downloads use a short-lived single-use download link, because a browser cannot stream a 10 GB file through an authenticated fetch.
- The existing single-CV endpoints go away; the stored CVs are moved into the cabinet (and marked master) by a one-time migration.
- UI: the "Your CV" section becomes a file cabinet with upload (with progress), a list, download, delete and "Use as master CV".

## Capabilities

### New Capabilities
- `file-cabinet`: storing, listing, downloading and deleting a user's files, and the master CV choice.
- `ui-file-cabinet`: the cabinet on the profile page.

### Modified Capabilities
- `cv`: the one-CV storage requirements move to `file-cabinet`; extraction reads the master CV.
- `ui-cv`: the CV section is replaced by the cabinet; the fill button needs a master.

## Impact

- Backend: new module `files` (disk storage, repository, controllers, migration, sweep), changes to `cv` (extraction source, old endpoints removed), changeset 011, multipart settings for 10 GB, module list in `ModularityTests`.
- UI: `ui/src/cv/` replaced by a cabinet module; profile page wiring.
- Cluster: a PersistentVolumeClaim for the backend and a mount; edge and gateway timeouts for long uploads must be checked on the real cluster.
- Operations: no total quota. The disk is the only limit (`min-free-bytes` keeps the node alive). Local-path volumes have no backup yet (see `k8s/README.md`).
- Backend and UI redeploy; `auth` untouched.
