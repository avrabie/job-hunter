# jobimport

Purpose: import a job from a URL the user pastes.

## Internal (`internal/`)
- `UrlRules`: accepts only https, port 443, no userinfo, a DNS name (no IP literal); builds the canonical URL (lower-case host, no fragment, no `utm_*`/`gclid`/`fbclid`, no trailing slash); refuses linkedin.com and indeed.com and their subdomains.
- `AddressRules`: public-address check (loopback, link-local incl. metadata, private, CGNAT, unique-local, multicast, unspecified, IPv4-mapped and NAT64 forms).
- `SafeFetcher`: resolves, requires every address to be public, connects a TLS socket to the validated address with SNI and certificate check for the name, GET over HTTP/1.1, at most 3 revalidated redirects, 10 s, 1 MB, HTML only. Failures are `FetchError` codes; URLs and content are never logged.
- `PageText`: jsoup to plain text, capped at 30 000 characters; under 300 characters is unreadable.

## API (`net.s4v3.backend.jobimport`)
- `JobImportService`: `preview(sub, ImportRequest)` fetches the page (or takes pasted text, no fetch), extracts with `llm.JobExtractor`, checks that at least 80 % of the description's words occur in the page text, and stores a draft (1 hour, owner-scoped). `confirm(sub, draftId, ImportEdits)` ingests it as source `user-import` (source posting id = sha256 of the canonical URL, `rawPayload` = the description), tracks it through `ApplicationService`, records `posting_import` (abuse handling only, never returned) and deletes the draft. `removeUserData(sub)` deletes a user's drafts and `posting_import` rows (no account deletion flow exists yet to call it). Failures are `JobImportException` with a fixed `ImportError` code.
- `JobImportController`: `POST /api/job-imports/preview` and `POST /api/job-imports`; errors are `{error, message}` with the statuses of the job-import spec, `Retry-After` on 429.
- Limits per user, in memory: `jobhunter.import.previews-per-hour` (20) and `jobhunter.import.confirms-per-day` (10).
- `ImportDrafts` (JDBC) and `DraftPurge` (hourly removal of expired drafts) are internal. Nothing is logged but codes, draft ids and durations.
