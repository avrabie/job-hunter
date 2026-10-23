# Tasks

## 1. Koog spike and learning note

- [x] 1.1 Add the Koog dependency (latest 1.x from Maven Central) and check it resolves with Kotlin 2.3 / JDK 25 / Boot 4.1; read the Koog pages on executors and structured output and write the first part of `docs/koog-notes.md`: what a prompt executor is, how the Anthropic one is created, how the model is chosen, and what `executeStructured` and a fixing parser do, each with a five-line example from our code. Verify with a test that uses a fake executor and passes without network or key, and with `KoogBoundaryTests`. If the API differs from this design, stop and propose a design update before continuing.

## 2. Typed extraction in the llm module

- [x] 2.1 Add `llm.internal` with the `@Serializable` draft DTO (`@LLMDescription` on class and properties, enums for levels, permits and remote modes, `YYYY-MM` strings) and the mapper to `Profile`, dropping single invalid values. Verify with unit tests: a full answer maps in order, unknown location and permit stay null, an invalid level or month drops only that value, an empty answer maps to an empty profile.
- [x] 2.2 Add the public `CvReader` interface and its Koog implementation (structured output, fixing parser with a small retry count, prompt that forbids guessing and quotes the CV as data, no tools), configuration `jobhunter.llm.model` and `ANTHROPIC_API_KEY`, and a fixed error for a missing key or a failed or unparseable answer. Verify with fake-executor tests for each `llm` scenario (well-formed, unparseable, no key, injection text stays inside the prompt data) and extend `docs/koog-notes.md` with how the pieces fit together.
- [x] 2.3 Add a log-capture test for the `llm` module (marker in the CV text never appears in captured logs, with root at INFO and `net.s4v3` at TRACE) and switch the logger `ai.koog` off in `application.yaml` (task 1.1 found that Koog logs the model's raw text at WARN when a structured answer fails to parse); add a test that a deliberately unparseable answer containing the marker leaves no marker in the logs, and a README note that Koog and HTTP client DEBUG logging must stay off.

## 3. CV storage and API

- [x] 3.1 Add changeset `010` (in `007-cv.sql`) (`cv_document`, FK with cascade) to the Liquibase master and a schema test. Verify the table, the primary key and the cascade on deleting an `app_user` row.
- [x] 3.2 Add the `cv` module: content checks (`%PDF-`, UTF-8 without NUL, 5 MB), display-name sanitising, store with replace, download and delete scoped by `sub`. Verify with integration tests for every `cv` scenario that does not involve extraction: upload and metadata, no CV, replace, each accepted kind, wrong content, too large, name sanitising, download bytes and headers, delete (also when none), cascade, user A cannot reach user B, anonymous 401.
- [x] 3.3 Add `CvController` (`POST /api/cv` multipart, `GET /api/cv`, `GET /api/cv/file`, `DELETE /api/cv`) with `@Operation` summaries, multipart limits, and fixed error bodies. Verify `ApiDocsContentTests` and `ModularityTests` still pass.
- [x] 3.4 Add text extraction (PDFBox for PDF, UTF-8 for text), the character cap, `POST /api/cv/extract` calling `CvReader`, the per-user hourly limit, and the 404, 413, 422, 429 and 502 answers. Verify with integration tests using a fake `CvReader` for every extraction scenario (draft returned and profile untouched, no CV, scanned PDF without a model call, limit without a model call, model failure with a fixed body) and a log-capture test through upload, extract and delete.

## 4. UI

- [x] 4.1 Add `ui/src/cv/` API functions (metadata, upload, download as blob, delete, extract) with fixed messages and no response bodies in results. Verify with vitest for each status mapping and that the token is only sent through the API client.
- [x] 4.2 Add the CV section to the profile page (empty, with CV, upload, replace, confirmed delete, download, error messages) and the "Fill the profile from my CV" step that puts the draft into the form as unsaved changes with a confirmation when saved content exists. Verify with component tests for every `ui-cv` scenario, including that `PUT /api/profile` is not called until Save.
- [x] 4.3 Run `npx vitest run`, `npx tsc -b && npx vite build`, `npx oxlint --deny-warnings` and `npx prettier --check .` in `ui/` and verify they pass.

## 5. Docs and integration

- [x] 5.1 Document the endpoints, limits, configuration and the Anthropic Secret in `backend/README.md`, `k8s/README.md`, `k8s/cv-secret.yaml.example` and the UI README; update `PLAN.md` and finish `docs/koog-notes.md`. Verify names match the code.
- [x] 5.2 Run the full backend build (`./gradlew build` in `backend/`) and verify it passes.

## Workflow follow-up

- With the owner: create the Anthropic key, put it in `k8s/private/anthropic.yaml` (git-ignored), apply the Secret; build and push backend and UI images, bump both tags, `kubectl diff`, apply (auth untouched).
- Manual check on s4v3.net with the owner's own CV: upload, extract, review, save, download, delete. Compare the draft with the CV and note quality problems for a later prompt change.
- Archive the change (new `cv`, `llm`, `ui-cv` specs).
