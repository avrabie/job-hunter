## Why

Postings only come from the crawled boards. A job the user finds elsewhere (a company career page, a recruiter link) cannot be tracked or used for a motivation letter. The user wants to paste a URL, have the app read the page, find the job, and track the application.

## What Changes

- **Import from URL**: the user pastes a job URL on the Job tracking page. The server fetches the page safely, an LLM extracts title, company, location and the plain-text description, and the user reviews the result before it is saved.
- **Paste fallback**: when a page cannot be read (needs login, renders with JavaScript, blocked), the same form accepts the pasted description text together with the URL; no fetch is made then.
- **Saved twice, by decision of the owner**: confirming creates a **shared posting** (visible in `/postings` to all users, marked "Added by a user") and tracks it as an **INTERESTED application** with the frozen job description. If the job already exists (same URL, or same company/title/city), the existing posting is tracked instead and nothing is duplicated.
- **Safety**: SSRF-hardened fetching, size and time limits, per-user rate limits, fixed error codes, nothing from the page or the URL in logs.
- Two new endpoints (`preview`, `confirm`), a new `jobimport` module, one new LLM service (`JobExtractor`), one Liquibase changeset, a new dependency for HTML-to-text (jsoup).
- Applications can exist without a posting: a new `source` (`POSTING` or `IMPORT`) on an application; imported ones have no live status comparison (always OPEN).

## Out of scope

- Scraping behind a login, running JavaScript, or headless browsers; LinkedIn and Indeed URLs are refused (project rule).
- Re-fetching an imported job later to detect changes (the posting's `last_seen` is not refreshed by imports).
- Sharing imported jobs with other users (a later change would need verified fetching, takedown and per-company ownership).
- Importing several URLs at once, browser extension or bookmarklet.
- Salary, workload and remote-mode extraction (fields stay empty).

## Capabilities

### New Capabilities
- `job-import`: fetch, extract, preview, confirm; limits and error codes.
- `ui-job-import`: the import form and review step on the Job tracking page.

### Modified Capabilities
- `applications`: tracking an imported job without a posting.
- `llm`: `JobExtractor` service.

## Impact

- Modules: new `jobimport` (depends on `applications`, `llm`); `applications` gains `trackImported`; `llm` gains `JobExtractor`; `postings` is not touched.
- Personal data and scoping: previews and imported jobs are per user and invisible to others.
- Claude cost: one small extraction call per preview (Haiku by default), limited per user per hour.
- New dependency: jsoup. Database: changeset `moldo:018` (tables `job_import_draft`, `posting_import`).
- Security surface: the server now makes outbound requests to user-given URLs (SSRF), and imported text is untrusted and reaches the letter model. Both are treated in design.
