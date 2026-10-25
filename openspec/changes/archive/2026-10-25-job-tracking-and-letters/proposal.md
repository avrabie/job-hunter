# Proposal

## Why

The app crawls postings and shows them, but a user cannot say "this one interests me" and the app forgets nothing about it that the user can rely on: the shared posting text is overwritten when the source changes and nothing tells the user a posting closed. Once a user decides on a job they need (1) their own list of jobs by stage, (2) the job description as it was when they decided, and (3) help writing a motivation letter that fits that job and uses their real experience. The owner asked for a job tracking section with the stages interested, applying, applied and rejected, and for letters written by the language model from the job description and the relevant parts of the profile, as plain Markdown text the user can review, comment on and regenerate.

## What Changes

- **Application tracking (new `applications` module).** A signed-in user marks a posting "interested" from the postings list or the posting page. That creates the user's own **application** record with a stage (`INTERESTED`, `APPLYING`, `APPLIED`, `REJECTED`), free notes and dates, and a **frozen copy of the job description** (title, company, link, plain-text description, capture time). The copy never changes when the shared posting is later refreshed or disappears. One application per user and posting.
- **Job Tracking page (UI).** A new page with the user's applications grouped by stage, quick stage changes, notes, the frozen description, a notice when the live posting changed or was last seen long ago, and removal.
- **Motivation letters.** From an application the user generates a letter: they choose language (EN or DE, default the posting's language), which experience entries, open source projects and skills to include, and optional instructions. The server sends the language model a tailored system prompt and a prompt built from the frozen job description and only the chosen profile parts. The result is Markdown text, shown for review.
- **Review loop.** The user can comment on the whole letter and on single paragraphs, then regenerate: the new version is written from the previous text, the job description, the chosen profile parts and the comments. The user can also edit the text by hand and save it as a new version. All versions are kept (at most 20 per application) and can be reopened, copied or downloaded as a `.md` file. Nothing is sent anywhere by the app; the user copies the letter.
- **Safety.** The job description is untrusted text from the internet and is handed to the model as data, never as instructions. The prompt forbids inventing facts, and the UI states that the letter must be checked. A per-user hourly limit applies, like the other model features. Job text, profile text, prompts and letters are never logged.

## Capabilities

### New Capabilities
- `applications`: the application record, stages, frozen job description, ownership and limits.
- `motivation-letter`: generation, versions, comments, regeneration, manual edit, download.
- `ui-applications`: the Job Tracking page and the application page with the letter workspace.

### Modified Capabilities
- `llm`: a `LetterWriter` that turns a job description, chosen profile parts, language, feedback and the previous text into Markdown.
- `ui-postings`: an "Interested" action on the list and on the posting page, showing the stage when the posting is already tracked.

## Impact

- Backend: the `applications` module gets Kotlin code (today it is an empty placeholder named in `ModularityTests`), two Liquibase changesets (`application`, `motivation_letter`), `llm` gets `LetterWriter` and its internal Koog implementation, settings `jobhunter.letters.*`. `postings` and `profile` are read through their public APIs only.
- UI: a nav entry, `/applications`, `/applications/:id`, small changes to `ui/src/postings`.
- Data: personal data (notes, letters). Never logged. Letters leave the system only to Anthropic as part of the request, containing only what the user selected.
- No new dependency.
