# Design

## Context

The profile is one JSON document per user (`Profile`, `GET`/`PUT /api/profile`, version check, `ProfileValidator`). `ModularityTests` and `KoogBoundaryTests` already exist; there is no `llm` module yet and no Koog dependency. The backend is Kotlin 2.3 on JDK 25, Spring Boot 4.1 on virtual threads; the newest changeset is 009, so the next is 010. The owner wants to learn Koog, so each Koog piece is introduced separately and explained.

## Goals / Non-Goals

**Goals:**
- Store, replace, download and delete one CV per user, as simply as possible.
- Turn CV text into a typed, reviewable `Profile` draft with Claude through Koog.
- A UI step where the user sees and edits the draft before anything is saved.
- A learning note (`docs/koog-notes.md`) that walks through the Koog code.

**Non-Goals:**
- Tailoring a CV or writing letters, a master-cv.md editor, OCR, versions, bring-your-own key, encryption, consent text, object storage.

## Decisions

- **Postgres `bytea`, table `cv_document`** (`owner_sub` primary key referencing `app_user` with `ON DELETE CASCADE`, `file_name`, `content_type`, `size_bytes`, `sha256`, `content`, `uploaded_at`). A CV is a few hundred KB; a row gives transactional replace and delete and backups for free. Granite-security's S3 design was the wrong shape here (see `handoff/granite-storage.report.md`).
- **Content decides the type.** `%PDF-` for PDF; strict UTF-8 without NUL for `.md`/`.txt` (the extension picks `markdown` or `plain`, content is still checked). The display name is sanitised and is never a path. Download is `attachment` with `nosniff`.
- **Size limit twice:** Spring multipart limit at 5 MB (answers 413) and a service check, so the limit does not depend on one setting.
- **Text extraction:** Apache PDFBox 3.x for PDFs (text only, no OCR; empty text means 422), UTF-8 decode for the others. Extracted text is capped (configurable, default 30,000 characters); over the cap is 413, never silently cut, so the model never sees a half CV.
- **Module layout.** `cv` (public: `CvService`-style API used by its controller only) calls `llm` through a small public interface `CvReader { fun profileFrom(text: String): Profile }`. `llm` depends on `profile` for the `Profile` type only. Koog types stay in `llm.internal`; the boundary test enforces it.
- **Typed output inside `llm.internal`.** A `@Serializable` draft DTO with `@LLMDescription` on the class and properties, using enums for levels, permits and remote modes and `"YYYY-MM"` strings for dates; a mapper turns it into `Profile`, dropping single invalid values (bad level, bad month) instead of failing. Reason: models make small mistakes, and the user reviews the draft anyway. The mapped profile is not saved; its field problems, if any, are left for the existing profile validation when the user presses Save.
- **Koog usage, in small steps** (the first task is a spike, because the Koog documentation pages checked while planning did not show the Anthropic executor signature): (1) one prompt to Claude through a Koog executor and print nothing sensitive; (2) the same call with structured output into the DTO and a fixing parser with a small retry count; (3) wrap it behind `CvReader`. No agent graph, no tools, no strategy yet: those come with CV tailoring. Koog 1.3.0 is the latest on Maven Central; the Koog Spring Boot starter is beta and is not used.
- **Model and key by configuration:** `jobhunter.llm.model` and `ANTHROPIC_API_KEY`; a missing key fails the call with a fixed error, not the startup. The key reaches the cluster through an optional Secret like `auth-google`, kept in `k8s/private/`.
- **Prompt-injection stance.** The CV is untrusted. It is sent as quoted data, the model has no tools, and the only output is a typed object shown to the user for review. The worst case is a wrong draft the user can see and correct.
- **Abuse and cost:** a per-user in-memory limit on extractions (default 5 per hour, configurable). One backend instance, so in-memory is enough.
- **No content in logs.** The application logs outcome, sizes and durations only. Koog or HTTP client DEBUG logging can print prompts and must stay off; the README says so and a log-capture test (marker through upload, extract, delete) enforces our own loggers.
- **Testing without Anthropic.** Tests use a fake `CvReader`, and the `llm` module is tested with Koog's mock or a fake executor; no CI test calls Anthropic. One manual check on the real model is a task after deploy.
- **UI.** A `ui/src/cv/` section on the profile page. Download fetches a blob with the bearer token and saves it (an anchor to `/api/cv/file` would send no token). The draft is applied to the form state as unsaved changes; the existing Save with the version check is the only way it reaches the server.

## Risks / Trade-offs

- Koog 1.x API details may differ from the plan; the spike limits the damage and may change the tasks.
- Extraction quality varies with CV layout (columns, tables). Mitigation: review step; improvements come from prompt and examples later.
- Scanned PDFs are refused (422) until OCR exists.
- The project config mentions an encrypted bring-your-own key; this change deliberately uses a server-side key and no encryption, as the owner decided for a private site. Revisit before opening the site to strangers.
- The CV leaves the server for Anthropic during extraction; no consent step is built, by the owner's decision.
