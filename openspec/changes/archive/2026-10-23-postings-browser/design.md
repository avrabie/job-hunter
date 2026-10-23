# Design

## Context

`postings` stores `posting` and `posting_source` (one row per source with `url`, `is_primary`, `last_seen`). `PostingQuery` (public) has `find` and `list`; `Posting` has no links. `web.internal.ProfileController` is the pattern for REST controllers, `OpenApiConfig` and the drift guard require a summary on every `/api` endpoint, and `SecurityConfig` requires a token for everything but health and the documentation. The UI (React, Vite, vitest) uses `apiFetch(auth, path)` with fixed messages per status (`api/messages.ts`) and has the routes `/`, `/profile`, `/register`, `/callback`; `pages/Shell.tsx` is the signed-in frame; the profile page and its API client (`profile/profileApi.ts`) are the pattern to copy.

## Goals / Non-Goals

**Goals:** let a signed-in user browse, search and open the original ads; keep the API small and the UI consistent with the profile page.

**Non-Goals:** match scores on the page, ranked search, public access, expiry.

## Decisions

### Public API of `postings`
Extend `PostingQuery` with `search(PostingSearch): List<Posting>` where `PostingSearch(text, canton, sourceId, page, size)` and a `links` field on `Posting`: `List<PostingLink(sourceId, url, primary)>`. `list` and `find` also fill `links`, so every caller gets them. The size cap stays in `PostingQuery.MAX_PAGE_SIZE`.

### Search in SQL
One query: `posting p` filtered by `(:text IS NULL OR p.title ILIKE :pattern ESCAPE '\' OR p.company ILIKE :pattern ESCAPE '\')`, `(:canton IS NULL OR p.canton = :canton)`, and `(:source IS NULL OR EXISTS (SELECT 1 FROM posting_source s WHERE s.posting_id = p.id AND s.source_id = :source))`, ordered by `p.first_seen DESC, p.id`, with `LIMIT/OFFSET`. The text is escaped (`\`, `%`, `_` get a backslash) before the pattern `%text%` is built, so wildcards in user input are literal. Links for the page's postings are read in one second query (`WHERE posting_id IN (...)`, primary first, then by source id), not one per row.

- *Alternative:* Postgres full-text search or `pg_trgm`. Better ranking, but extra setup and a language choice for a few hundred rows; revisit when the table is large or search quality matters.

### Endpoints in `web`
`PostingsController` in `web.internal` next to `ProfileController`: `GET /api/postings` and `GET /api/postings/{id}`, using only `PostingQuery`. DTOs map the public type to JSON: list items without the description, the detail with it; neither has the raw payload (the type never contains it). Validation: negative `page` → 400, `size` clamped to 1..100, a blank `q` treated as absent, `canton` upper-cased and limited to two letters (otherwise ignored → 400 for anything else), `source` limited to a safe id pattern. `@Operation` summaries and `@ApiResponse`s are added so the drift guard passes. The search text is never logged; no log line is added for these endpoints.

### UI
New files under `ui/src/postings/`: `postingsApi.ts` (typed `loadPostings(auth, query)` and `loadPosting(auth, id)` returning result unions like `profileApi.ts`, with fixed messages), `PostingsPage.tsx` (filters, list, paging), `PostingPage.tsx` (detail), `links.ts` (`safeHttpUrl(url)` accepting only `http:` and `https:`), tests beside them. Routes `/postings` and `/postings/:id` in `App.tsx`; a "Postings" link in the signed-in shell. Filters live in the URL query (`?q=&canton=&source=&page=`), so a filtered view can be bookmarked and the back button works. The description is rendered as text inside a `<pre>`-style element with `white-space: pre-wrap` (React escapes it), never as HTML. Canton choices are the 26 Swiss canton codes; source choices come from the sources seen in the current result's links (no separate endpoint).

- *Alternative:* a public page. Postings are public ads, but the whole product is behind sign-in and a public list would invite scraping of our crawl; not asked for.

### Module boundaries and database
`web` → `postings` public API only. `postings` keeps its schema. Existing `MatchStore` keeps its own link subquery; replacing it with the new API is left alone.

## Risks / Trade-offs

- [`ILIKE '%x%'` does not use an index] → fine for thousands of rows; the note above says when to move to trigram.
- [A hostile URL from a source (`javascript:`)] → only `http`/`https` render as anchors (tested), and links open with `noopener noreferrer`.
- [Descriptions contain HTML-looking text or contact names] → rendered as text; contact details stay inside the ad text as published and are not logged.
- [Large lists] → size cap of 100 and paging in the API; the UI uses page sizes of 20.
- [The source filter lists only sources in the current page] → acceptable for now; a facet endpoint can come later.

## Migration Plan

No schema change. Deploy the backend, then the UI image; the UI can ship first only if it handles a 404 on `/api/postings` (it shows the fixed failure message). Check on s4v3.net: open `/postings`, search, open an original link, open a detail page, and confirm `GET /api/postings` answers 401 without a token. Rollback: redeploy the previous images.

## Open Questions

- None blocking. Showing the user's match score next to each posting is the natural follow-up change.
