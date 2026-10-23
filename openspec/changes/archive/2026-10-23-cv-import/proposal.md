# Proposal

## Why

Users must type their whole profile by hand. A CV they already have holds most of it. Storing one CV per user and turning it into a reviewable profile draft is the first step towards the site's main feature (CVs and letters tailored to a job description). It is also the first use of Koog, so it should be small enough to understand end to end.

## What Changes

- New `cv` module: a signed-in user uploads one CV (PDF, `.md` or `.txt`, up to 5 MB), downloads it again, replaces it by uploading another, or deletes it. It is stored in Postgres, one row per user, and kept until the user deletes it.
- New `llm` module (the only place that imports Koog): reads CV text and returns a typed profile draft produced by Claude. The draft is never saved by the server.
- New `POST /api/cv/extract`: builds the draft from the stored CV. The UI shows it in the profile form for review and the user saves it with the existing `PUT /api/profile`.
- UI: a CV section on the profile page (upload, file info, download, delete, "Fill the profile from my CV").
- A learning note `docs/koog-notes.md` that explains each Koog piece as it is introduced.
- Operational: the Anthropic API key is a server-side Secret (`ANTHROPIC_API_KEY`), applied like `auth-google`. No consent flow, no encryption and no legal text: the site is private to the owner and friends.
- CV text, prompts and model answers are never logged.

## Capabilities

### New Capabilities
- `cv`: storing, downloading, replacing and deleting a user's single CV, and starting an extraction from it.
- `llm`: the typed CV-to-profile extraction through Koog and Claude.
- `ui-cv`: the CV section of the profile page and the review step before saving.

### Modified Capabilities

## Impact

- Backend: new changeset `010` (`cv_document`), new packages `cv`, `llm`, dependencies Apache PDFBox and Koog (`ai.koog`), multipart limits, per-user rate limit on extraction, `KoogBoundaryTests` now applies to real code.
- UI: new `ui/src/cv/` code and a section in the profile page.
- Cluster: new optional Secret with the Anthropic key; backend env var; backend and UI redeploys (auth untouched).
- Out of scope, later changes: tailoring a CV to a job description, motivation letters, the master-cv.md editor page, OCR for scanned PDFs, CV versions, bring-your-own key.
