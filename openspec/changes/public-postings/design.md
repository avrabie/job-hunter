## Context

`GET /api/postings` (signed-in) pages by `page`/`size` with `q`, `canton`, `source`; `PostingQuery.search(PostingSearch)` serves it. Public endpoints exist for public CVs and the API docs in `SecurityConfig`. The gateway is stateless (no rate limiting). The landing page shows no data and makes no `/api` call today.

## Goals / Non-Goals

**Goals:** a public, paginated, date-bounded list of recent postings; a landing page section; no legal exposure from republishing job text.

**Non-Goals:** descriptions, search text (`q`), employer postings, caching layer outside the app, indexing the API by crawlers.

## Decisions

**Metadata only.** Public fields: `id`, `title`, `company`, `city`, `canton`, `remoteMode`, `salaryMin`, `salaryMax`, `language`, `firstSeen`, `links` (source label and URL of the original page). The detail endpoint and the description remain behind sign-in. The id lets a visitor continue at `/postings/{id}` after signing in.

**Paging and dates.** `size` default 20, at most 50. `days` (1 to 31) or `since`/`until` (ISO dates, Zurich zone, inclusive; the window at most 31 days; `since` not before 90 days ago). With nothing given, the default is `days=14`. Order is `firstSeen` descending with the id as tiebreak, so paging does not skip or repeat rows. `page` times `size` is capped at 1000 to stop deep scans; beyond it the answer is 400. The body carries `total` for the filtered window so the client can show pages.

**Filters.** `canton` (two-letter code) and `language` (en or de). No free-text search: `q` is user text that must not reach logs and is a heavier query; it stays on the signed-in list.

**Open-endpoint protection.** An in-memory limiter per client address (default 60 requests per minute; the address is the first `X-Forwarded-For` value set by the edge, else the peer address) answers 429 with `Retry-After`. The state is bounded (old entries evicted) so a spray of addresses cannot grow memory. Responses carry `Cache-Control: public, max-age=60`. Honest limit: `X-Forwarded-For` could be spoofed if a request bypassed the edge; the cluster's network policies only let the edge reach the gateway, and the limit is a cost guard, not security.

**Landing page.** Fetches the first page of 6 with no token; renders a list with title, company, city, a short "date" and "Sign in to read more". If the call fails or returns nothing, the section is simply absent; the page never blocks or shows an error. The landing makes this one call and no other `/api` call.

**Reuse.** The date bounds extend `PostingSearch` with optional first-seen bounds; a separate public mapper builds the response, so no description or raw payload can leak by adding a field to the shared one.

## Risks / Trade-offs

- Re-listing titles and companies of scraped jobs still republishes metadata; mitigated by links back to the source page and no text. Per-source switch is possible later if a source objects.
- A public list is scrapable; the window, page cap, size cap, rate limit and cache bound the cost.
- Caching means up to 60 s of staleness; acceptable.

## Migration Plan

No schema change. Deploy backend and ui. Rollback: previous tags.

## Open Questions

- Whether `robots.txt` (separate change) should allow the public API path.
