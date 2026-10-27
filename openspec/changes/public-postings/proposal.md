## Why

The crawled postings are visible only to signed-in users. The owner wants the most recent postings public, paginated and filterable by date (for example the last two weeks), and shown on the logged-out landing page. The descriptions are other parties' text (SwissDevJobs has no terms that allow reuse), so the public tier gives metadata and a link back to the source page only; the description stays behind sign-in.

## What Changes

- New public endpoint `GET /api/public/postings`: paginated, newest first, with date filters (`days`, or `since` and `until`), optional `canton` and `language`; fields title, company, city, canton, remote mode, salary range, language, first-seen date and the source links. No description, no raw payload.
- Protection for an open endpoint: a bounded window and page size, a per-client request limit (429 with `Retry-After`), and a short public cache.
- The landing page shows the latest postings with "Sign in to read more"; it stays free of user data.
- Employer-submitted postings are out of scope (later change).

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `postings`: adds the public postings endpoint.
- `ui-sign-in`: the landing page may show the public latest postings.
- `ui-postings`: adds the landing list.

## Impact

- Backend: `postings` (date-bounded query and a public controller), `accounts` `SecurityConfig` (one more permitted GET), a small in-memory per-client limiter, OpenAPI.
- UI: landing page section, a public client call without a token.
- Gateway: `/api` is already routed; no change expected. The gateway is stateless and has no rate limiting, so the limit lives in the backend.
- No schema change. An index on `first_seen` is added only if the query plan needs it.
