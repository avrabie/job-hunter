# Proposal

## Why

The backend crawls Swiss IT postings every hour and stores them with the original link of each ad, but nobody can look at them. No HTTP endpoint serves postings (`PostingQuery` is internal, and `GET /api/matches` lists only a user's own passing matches), and the UI has only the sign-in and profile pages. A page with every crawled posting and its original links makes the crawl visible, lets the owner check what the crawler keeps and misses (for example the IT filter), and is the base for showing matches later.

## What Changes

- **Backend:** two read endpoints for any signed-in user (postings are shared):
  - `GET /api/postings?page=&size=&q=&canton=&source=` lists canonical postings, newest first-seen first, with title, company, city, canton, language, first and last seen, and **all original links** (one per source that delivered the ad, the primary one first). `q` is a case-insensitive text search over title and company; `canton` and `source` filter exactly. Size is capped at 100.
  - `GET /api/postings/{id}` returns one posting with its plain-text description and links, or 404.
  - Neither returns the raw payload.
- **`postings` module:** `PostingQuery` gains a search with the filters above and a way to read a posting's source links; the posting type gains the links. The HTTP controller lives in `web` (the REST layer for the UI).
- **UI:** a `/postings` page (reachable from the signed-in shell) with a search box, canton and source filters, paging, and per posting the title, company, city, language, the dates and an **Open original** link for each source (new tab, `rel="noopener noreferrer"`); a `/postings/:id` page with the full description. Only `http` and `https` links are rendered as links.
- Both endpoints appear in the generated API description (the drift guard requires summaries).

### Observed about the current code

- `posting` holds the normalized record; `posting_source (posting_id, source_id, source_posting_id, url, is_primary, last_seen)` holds one row per source. A posting delivered by two sources has two links; the canonical row's primary source is the first one.
- `PostingQuery` has `find(id)` and `list(page, size)`; both return `Posting` without links. `MatchStore` reads one link per posting by a subquery.
- The UI has the routes `/`, `/profile`, `/register`, `/callback`; pages use `apiFetch` with a token source and show fixed messages for failures (no response bodies). The signed-in shell exists in `pages/Shell.tsx`.
- 40 Swiss IT postings from Greenhouse exist in production today; the number grows with each hourly crawl.

### Out of scope

- Showing a user's match score or reasons on this page (a later change can join `GET /api/matches`).
- Editing, hiding, saving or deleting postings; applying; notifications.
- Ranked full-text search or fuzzy matching (a plain `ILIKE` is enough for hundreds of postings).
- Expiry of postings that left the source; a "closed" flag.
- Making the list public without sign-in.

## Capabilities

### New Capabilities
- `ui-postings`: the postings list and detail pages.

### Modified Capabilities
- `postings`: read API with search and source links, and the two HTTP endpoints.

## Impact

- **Modules:** `postings` (public API extended, no schema change), `web` (new controller), UI build (new pages and API client). No new module dependencies: `web` already depends on `postings`' public API pattern like it does for `profile`.
- **Database:** none; one extra index on `posting (lower(title))` is not needed at this size.
- **Dependencies:** none.
- **Personal data:** postings are public ads; the endpoints return no user data. The search text is not logged. **Per-user scoping:** none (shared data); a token is still required. **Claude cost:** none.
