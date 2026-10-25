## Context

Observed in the code: `postings.PostingIngest.ingest(PostingCandidate)` stores or refreshes a posting by (source id, source posting id), de-duplicates across sources by `dedupe_key` and returns an existing posting when another source already has it; only the primary source may change canonical fields. `posting_source.source_id` is plain text, so a new source id needs no schema change. `applications` already tracks a posting by id and freezes its text. `sources.internal.PoliteHttp` fetches JSON for crawlers and is internal to `sources`, follows no redirects and does no address checks. `llm` holds Koog behind small interfaces (`LetterWriter`, `CvReader`) with fixed `LlmFailure` messages.

## Decisions

1. **New module `jobimport`.** It orchestrates fetch, extract, preview, confirm. It may depend on `postings`, `applications`, `llm`; nothing depends on it. Putting it in `applications` would pull outbound HTTP into a per-user module; putting it in `sources` would make crawlers depend on `applications`.
2. **Two steps with a server-held draft.** `POST /api/job-imports/preview` takes `{url}` or `{url, text}` and returns `{draftId, title, company, location, description, language}`. The draft is stored in `job_import_draft` (owner, expires after 1 hour). `POST /api/job-imports` takes `{draftId, title?, company?, location?, description?}` and creates posting plus application. Edits are allowed within field limits, so the user can fix what the model got wrong; the confirm never accepts a URL (taken from the draft), so a user cannot attach arbitrary text to arbitrary URLs.
3. **SSRF-hardened fetcher (own code, no new HTTP library).**
   - https only, port 443 only, no userinfo in the URL, host must be a DNS name (no IP literals).
   - Resolve the host, reject if **any** address is loopback, link-local (incl. 169.254.169.254), private, CGNAT, unique-local, multicast, unspecified or IPv4-mapped forms of those. Connect to the **validated address** with TLS SNI and Host set to the name (a small `SSLSocket` HTTP/1.1 GET), so DNS rebinding between check and connect is not possible.
   - Redirects: manual, at most 3, each hop fully revalidated, https only.
   - Limits: 10 s total, 1 MB body, `Accept-Encoding: identity` (no decompression bombs), content type `text/html` or `application/xhtml+xml` only.
   - Defence in depth: a Kubernetes `NetworkPolicy` for the backend pod denying egress to private ranges and the metadata address, added to `k8s/` and validated by `k8s/validate.sh`.
   - Denylist of hosts the project does not scrape (linkedin.com, indeed.com and subdomains) → `unsupported_site`.
   - One request per user action, identifying `User-Agent`; robots.txt is not consulted because this is a single user-initiated fetch, not crawling (recorded as an assumption for the owner).
4. **HTML to text with jsoup** (new dependency, version pinned from Maven Central at apply time): drop script/style/nav/footer/form, collapse whitespace, cap at 30 000 characters. The standard library cannot parse real-world HTML safely. Text under 300 characters means a JavaScript-only or empty page → `unreadable` with the paste hint.
5. **`llm.JobExtractor`** returns `JobDraft(found, title, company, location, description, language)`. The page text is untrusted data inside `<page_text>` delimiters; the system prompt allows only extraction (no summarising, no invention, no instructions followed), requires `found=false` when the page is not one job ad. Output checks: non-blank title and company, lengths capped, no HTML, and a **grounding check**: at least 80 % of the description's words must occur in the page text, otherwise `extraction_failed`. Model setting `jobhunter.import.model` (empty = `jobhunter.llm.model`).
6. **Posting identity and dedupe.** `sourceId = "user-import"`, `sourcePostingId = sha256(canonical URL)` where canonical = lower-case host, no fragment, tracking parameters (`utm_*`, `gclid`, `fbclid`) removed, trailing slash trimmed. `ingest` then handles same-URL and same company/title/city. Confirm returns `{applicationId, postingId, created, postingExisted}`; if the user already tracks the posting, `created=false`.
7. **Shared-posting implications (owner chose shared).**
   - *Abuse/spam*: any signed-in user can add text that all users see and that matching scores. Limits: previews 20 per user per hour, confirms 10 per user per day; configurable; limiter state in memory like `LetterLimiter`.
   - *Attribution*: `posting_import(posting_id, user_sub, url, created_at)` records who added it, for abuse handling only; never in any API response; row removed on account deletion, the posting stays (it is a public job ad).
   - *Visibility*: `PostingQuery` exposes `userAdded` (primary source is `user-import`); the UI shows an "Added by a user" badge so nobody mistakes it for a crawled, verified source.
   - *Injection*: stored text is rendered as plain text everywhere and is already treated as untrusted data by the letter prompt; this change adds no new rendering path.
   - *Takedown*: no admin removal in this change (out of scope, follow-up), so the risk is accepted for a small multi-user deployment and recorded for the owner.
8. **Errors** are fixed codes with fixed messages: `invalid_url`, `unsupported_site`, `blocked_address`, `unreachable`, `not_html`, `too_large`, `unreadable`, `not_a_job`, `extraction_failed`, `rate_limited`, `draft_not_found`, `llm_unavailable`. The URL, page text, model output and draft fields are never logged; logs carry only the error code, a draft id and duration.
9. **Schema (Liquibase `moldo:018-job-import`)**: `job_import_draft(id uuid pk, owner text, url text, title, company, location, description, language, created_at, expires_at)` with index on owner; `posting_import(posting_id uuid pk references posting, user_sub text, url text, created_at)`. A scheduled purge of expired drafts reuses the existing scheduling mechanism.

## Contract

- `POST /api/job-imports/preview` → 200 `{draftId, title, company, location, description, language}`; 400/422/429/502 with `{error: <code>}`.
- `POST /api/job-imports` → 201 `{applicationId, postingId, created, postingExisted}`; 404 `draft_not_found` for another user's or expired draft.
- Public Kotlin API: `JobImportService.preview(userSub, request)` and `confirm(userSub, draftId, edits)`; `llm.JobExtractor.extract(pageText, url host)`; `postings.PostingQuery` gains `userAdded`.
- No new module events.

## Risks / Trade-offs

- Own raw-socket HTTP is more code than `HttpClient` but is the only way to pin the validated address; covered by tests against a local TLS fake with rebinding and redirect cases.
- LLM extraction can still err; the user reviews and edits before saving, and the grounding check blocks invented descriptions.
- Shared spam risk (decision 7) has no admin removal yet.
- Some company sites block server fetches; the paste fallback is the answer.
