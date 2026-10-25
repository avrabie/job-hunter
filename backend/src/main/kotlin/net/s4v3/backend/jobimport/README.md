# jobimport

Purpose: import a job from a URL the user pastes.

## Internal (`internal/`)
- `UrlRules`: accepts only https, port 443, no userinfo, a DNS name (no IP literal); builds the canonical URL (lower-case host, no fragment, no `utm_*`/`gclid`/`fbclid`, no trailing slash); refuses linkedin.com and indeed.com and their subdomains.
- `AddressRules`: public-address check (loopback, link-local incl. metadata, private, CGNAT, unique-local, multicast, unspecified, documentation ranges, 6to4, Teredo, `::/96`, IPv4-mapped and NAT64 forms).
- `SafeFetcher`: resolves, requires every address to be public, connects a TLS socket to a validated address (the next validated one when a connect fails) with SNI and certificate check for the name, GET over HTTP/1.1, at most 3 revalidated redirects, 10 s, 1 MB, HTML only. Failures are `FetchError` codes; URLs and content are never logged.
- `PageText`: jsoup to plain text, capped at 30 000 characters; under 300 characters is unreadable.

## API (`net.s4v3.backend.jobimport`)
- `JobImportService`: `preview(sub, ImportRequest)` fetches the page (or takes pasted text, no fetch), extracts with `llm.JobExtractor`, checks that at least 80 % of the words of the description, the title and the company occur in the page text, and stores a draft (1 hour, owner-scoped). `confirm(sub, draftId, ImportEdits)` creates only an application of the caller through `ApplicationService.trackImported` (`source = IMPORT`, no posting, never shared, live status OPEN) and deletes the draft; the same canonical link imported again returns the existing application (`created=false`). `removeUserData(sub)` deletes a user's drafts (no account deletion flow exists yet to call it). Failures are `JobImportException` with a fixed `ImportError` code.
- `JobImportController`: `POST /api/job-imports/preview` and `POST /api/job-imports`; errors are `{error, message}` with the statuses of the job-import spec, `Retry-After` on 429.
- Limits, in memory: per user `jobhunter.import.previews-per-hour` (20) and `confirms-per-day` (10); previews in flight 2 per user (`max-concurrent-previews-per-user`) and 8 in total (`max-concurrent-previews`), answered 429 `rate_limited`; `ImportBodyLimit` refuses import request bodies over 256 KB (413).
- `ImportDrafts` (JDBC, drafts only) and `DraftPurge` (hourly removal of expired drafts) are internal. Nothing is logged but codes, draft ids and durations.
