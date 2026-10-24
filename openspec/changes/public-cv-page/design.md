# Design

## Context

- `Profile` is a JSON document per user (`StoredProfile(version, profile)`); `cv.export` has `CvBuilder` (profile + `ExportOptions` to `CvDocument`), `HtmlRenderer` (escaping, CSP meta, three templates) and `Labels`. `SecurityConfig` permits only a few GET paths without a token (`/api/files/*/content` is the model) and requires ADMIN for `/api/admin/**`.
- Edge: Traefik sends `/` to the UI pod (nginx, SPA fallback) and `/api`, `/auth` to the Spring gateway, which forwards without rewriting and adds nosniff, X-Frame-Options, Referrer-Policy, HSTS but no CSP. The gateway trusts the edge's `X-Forwarded-For` (the client's address), the backend has no forwarded-header strategy. Traefik's access log is disabled (`k8s/platform/traefik-values.yaml`), so paths are not logged there. The NetworkPolicy lets only the edge namespace reach the gateway.
- The UI sets `Content-Security-Policy: ... style-src 'self'` (no `unsafe-inline`; `ui/test-image.sh` asserts it). A sandboxed `srcdoc` frame inherits its parent's policy, so the CV's inline styles are very probably blocked inside the app's export preview (**to verify in a browser on the deployed UI; jsdom tests cannot see it**). A document served by the backend as a top-level page has only the backend's own headers.
- Owner decisions: user-chosen handle; per-field visibility, everything off by default; **always live** (not a snapshot).

## Goals / Non-Goals

**Goals:** a safe, public, per-user CV page at a chosen handle that always shows the current profile in the fields the owner ticked; one builder for export and public page; no new dependency.
**Non-Goals:** see proposal.

## Decisions

1. **Live rendering, cached by version.** The page is rendered from the owner's stored profile at request time and cached in memory keyed by `(owner sub, profile version, settings version, template, language)` (at most 500 entries, 10 min). A saved profile edit changes the version, so the next request re-renders; the ETag is the SHA-256 of the HTML. Deviation from the advisor's recommendation (`handoff/cv-ideas.md`, snapshot): a half-finished edit is public as soon as it is saved. Mitigations: everything is off by default and per field, a banner on the profile page and the Public CV page says the page is live, **Pause** is one click and takes effect on the next request (no shared cache: `Cache-Control: no-cache` plus ETag), the preview shows what is public now, and an audit trail records changes of settings. A snapshot mode can be added later as an option without changing the address.
2. **Handle rules.** Stored lowercase, `[a-z0-9]` with single inner hyphens, 3 to 30 characters, unique (case-insensitive unique index), not on the reserved list (below). One handle per user. A change is allowed at most once per 30 days; the old handle is **held for 90 days**: nobody else can claim it, the owner can take it back; after that it is free. Taking over an old link is therefore not possible for 90 days. Alternative (advisor): random token; the owner chose handles, so the risks (squatting, impersonation, enumeration) are met by the reserved list, the hold, the rate limits and the admin takedown, and by there being no list or search endpoint of handles.
3. **Reserved handles.** `admin, administrator, api, auth, login, logout, register, callback, cv, cvs, export, profile, files, file, matches, postings, posting, publish, public, settings, setting, account, accounts, user, users, me, root, support, help, about, contact, terms, privacy, legal, imprint, www, mail, static, assets, health, actuator, docs, swagger, openapi, new, edit, null, undefined, s4v3`, plus any handle of 3 or more characters that is only digits. The list lives in one Kotlin constant and a test checks every UI route name (`App.tsx`) is on it.
4. **Settings object (all default off).** `{template: CLASSIC|MODERN|COMPACT, language: EN|DE, sections: [CvSectionKind...] (ordered, default empty), headline, analysis, workAuthorisation, location, contact: {email, phone, website, linkedin, codeAccounts}, indexable: false}`. The full name is always printed (a CV page without a name makes no sense); everything else only when ticked. Preferences are never printed. `OPEN_SOURCE` in `sections` includes projects and their notes and technologies; `analysis` adds the analysis summaries.
5. **One builder.** `ExportOptions` gains `contact: ContactVisibility = ContactVisibility.ALL` (`email, phone, website, linkedin, codeAccounts, location, headline`, each Boolean, default true), so export is unchanged; the public path passes the owner's flags. `CvBuilder` stays the only place that decides what is printed, so the "never preferences" test covers both.
6. **Serving.** `GET /api/public/cv/{handle}` (backend, `cv.publiccv.internal`), a single `permitAll` GET matcher with explicit pattern `/api/public/cv/*`. Headers: `Content-Type: text/html; charset=utf-8`, `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox`, `X-Robots-Tag: noindex, nofollow, noarchive, nosnippet` (or `index, follow` when `indexable`), `Referrer-Policy: no-referrer`, `Cache-Control: no-cache`, `ETag`, `X-Content-Type-Options: nosniff`. The page has no scripts and `<meta name="robots">` matching the header; outgoing links carry `rel="noopener noreferrer nofollow"`. `If-None-Match` answers 304. Unknown, paused, removed, locked, never-published, owner-without-name and malformed handles all answer the **same** 404 body (`{"error":"not_found"}`).
7. **Pretty address `/cv/{handle}`.** One HTTPRoute rule in `k8s/base` (PathPrefix `/cv/`, `URLRewrite` `ReplacePrefixMatch: /api/public/cv/`, backend: the gateway Service, more specific than the `/` rule to the UI). It makes `https://s4v3.net/cv/ada-example` return the backend page directly. **Verify on the cluster first**: that Traefik v3 (the version in `k8s/platform/traefik-values.yaml`) supports the `URLRewrite` filter with `ReplacePrefixMatch`; if it does not, use the direct address `/api/public/cv/{handle}` as the shareable link (the UI constant `PUBLIC_CV_PATH` decides which one is shown) and record the limitation. The UI does not get a `/cv/:handle` route (it would also hit the UI's CSP).
8. **Owner preview.** `POST /api/cv/public/preview` with the settings returns `{path}` of a **one-use, 60-second ticket** (`GET /api/cv/public/preview/{ticket}`, no token, same headers as the public page but `Cache-Control: no-store` and `X-Robots-Tag: noindex`), bound to the owner and the posted settings, rendered from the owner's profile at ticket use; the UI opens it in a new tab. It never inlines the HTML into the app (CSP inheritance, and the app origin stays free of rendered user HTML). Tickets are in memory (copy of the idea in `files/internal/DownloadTickets`, own class in `cv.publiccv.internal`, injectable `Clock`).
9. **Limits.** In-memory sliding-window limiter `WindowLimiter(limit, window)` (own small class): per client IP 60 requests/minute on the public page, a stricter 10/minute per IP for 404 answers, per handle 600/minute in total (so a spoofed or shared IP cannot turn the page into a DoS tool), 429 with `Retry-After`. Client IP: the first value of `X-Forwarded-For` (set by the edge), else the socket address; the per-handle cap is the backstop when the header is wrong. Owner endpoints: 60 requests/hour per user for handle checks, 30/hour for previews.
10. **Data (Liquibase `009-public-cv.sql`).** `public_cv(owner_sub PK -> app_user ON DELETE CASCADE, handle UNIQUE lower-case, settings JSONB, published BOOLEAN, locked BOOLEAN default false, settings_version INT, handle_changed_at TIMESTAMPTZ, updated_at)`, `public_cv_handle_hold(handle PK, owner_sub -> app_user ON DELETE CASCADE, held_until TIMESTAMPTZ)`, `public_cv_audit(id, owner_sub -> app_user ON DELETE CASCADE, event, at, flags JSONB)` where `flags` records which settings are on (never values) and the events are `PUBLISHED, PAUSED, SETTINGS_CHANGED, HANDLE_CHANGED, REMOVED, TAKEN_DOWN, UNLOCKED`. Expired holds are purged by the existing scheduling mechanism (a daily `@Scheduled` job, one query).
11. **Admin takedown.** `DELETE /api/admin/public-cv/{handle}` sets `published = false`, `locked = true` (the owner cannot publish again), audits `TAKEN_DOWN`; `POST /api/admin/public-cv/{handle}/unlock` clears the lock. Both answer 404 for an unknown handle. Covered by the existing `/api/admin/**` ADMIN rule.
12. **Module placement.** Package `net.s4v3.backend.cv.publiccv` inside the existing `cv` module (public: `PublicCvService`; internal: controllers, repository, limiters, cache, ticket store, reserved list). It calls the public `ProfileService` and the `cv.export` builder and renderer. `profile` does not import `cv`. Account deletion needs no event: the foreign keys cascade.
13. **Logging.** Never log handles together with IPs, profile text or the settings; log status, duration and the first 8 characters of the ETag. The owner endpoints log only outcomes.

## Contract (do not change)

Management, authenticated (`/api/cv/public`):
- `GET /api/cv/public` -> 200 `{"configured":false}` or `{"configured":true,"handle":"ada-example","published":true,"locked":false,"settings":{...},"handleChangeableAt":"2026-11-23T10:00:00Z"|null,"updatedAt":"..."}`.
- `PUT /api/cv/public` body `{"handle":"ada-example","published":true,"settings":{...}}` -> 200 the same shape as GET. Errors: 400 `{"error":"validation_failed","fields":{"handle":"..."}}`, 409 `{"error":"handle_taken"|"handle_reserved"|"handle_held"|"handle_change_too_soon"|"name_required"|"locked"}`, 429.
- `GET /api/cv/public/handle?handle=x` -> `{"available":true}` or `{"available":false,"reason":"invalid|reserved|taken|held"}`; the owner's own current handle is `available`.
- `POST /api/cv/public/preview` body `{"settings":{...}}` -> `{"path":"/api/cv/public/preview/<ticket>"}`; `GET /api/cv/public/preview/{ticket}` (no token) -> the HTML once, then 404.
- `DELETE /api/cv/public` -> 204; the settings are removed, the handle is held 90 days.
Public (no token): `GET /api/public/cv/{handle}` -> 200 HTML, 304, or 404 `{"error":"not_found"}`; 429 `{"error":"rate_limited"}` with `Retry-After`.
Admin: `DELETE /api/admin/public-cv/{handle}` -> 204/404, `POST /api/admin/public-cv/{handle}/unlock` -> 204/404.
Settings JSON: `{"template":"MODERN","language":"EN","sections":["EXPERIENCE","SKILLS"],"headline":true,"analysis":false,"workAuthorisation":false,"location":true,"contact":{"email":false,"phone":false,"website":true,"linkedin":true,"codeAccounts":true},"indexable":false}`; every field defaults to false/empty, `template` defaults `CLASSIC`, `language` `EN`.
Kotlin (package `net.s4v3.backend.cv.publiccv`): `data class PublicCvSettings(...)`, `data class PublicCvState(handle, published, locked, settings, handleChangeableAt, updatedAt)`, `interface PublicCvService { fun state(sub); fun save(sub, handle, published, settings): SaveResult; fun remove(sub); fun renderPublic(handle): RenderedPage?; fun preview(sub, settings): PreviewTicket; fun renderPreview(ticket): RenderedPage?; fun takeDown(handle): Boolean; fun unlock(handle): Boolean }`, `data class RenderedPage(html: ByteArray, etag: String, indexable: Boolean)`. In `cv.export`: `data class ContactVisibility(email, phone, website, linkedin, codeAccounts, location, headline: Boolean = true)` with `ContactVisibility.ALL` and `.NONE`, and `ExportOptions.contact`.

## Threat model

| Threat | Mitigation |
|---|---|
| Enumerating handles | Uniform 404 for every non-served state, stricter limit on 404s, no list or search endpoint, per-handle cap |
| Squatting and impersonation of a real person's name | Reserved list, one handle per user, owner-controlled content only, 90-day hold of released handles, admin takedown and lock |
| Taking over an old shared link | 90-day hold |
| Doxxing (contact, employer chain) | Everything off by default and per field, preview before publishing, banner that the page is live, preferences never printed |
| Live edit leaks a half-written change | Pause, audit, banner; snapshot mode possible later |
| Stored XSS from profile text on the app's origin | Single `esc()`, no scripts in the page, CSP `default-src 'none'` with `sandbox` (opaque origin), `nosniff`, links only http(s) with `noopener noreferrer nofollow` |
| Search-engine and archive copies | `noindex, noarchive` by default; indexing is an explicit option with a warning; copies cannot be recalled |
| Scraping a known handle | IP and handle limits, `noindex`; inherent for any public page |
| DoS through rendering | Cache keyed by version, per-handle cap, shared PDF semaphore not used (HTML only) |
| Handle or content in logs | No path or content logging, Traefik access log is disabled, only status and duration are logged |
| Spoofed `X-Forwarded-For` evades the IP limit | Per-handle cap and 404 limit as backstop; the edge sets the header |
| Account deleted but page stays | Foreign keys cascade; test deletes the user and expects 404 |

## Risks / Trade-offs

- [Live page leaks drafts] -> see decision 1; revisit if the owner sees it happen.
- [Traefik cannot rewrite `/cv/`] -> fallback to the direct address; the rewrite is a config-only follow-up.
- [Owner preview in a new tab is less convenient than inline] -> forced by the UI's CSP; a later change can add a stylesheet-link mode for inline preview (also repairing the export preview if the browser check shows it unstyled).
- [Handles are public identifiers and can reveal a real name] -> the owner chooses them, the page says so.

## Migration Plan

Deploy backend and UI together; apply the HTTPRoute rule with `kubectl diff` first. No data migration. Rollback: previous images ignore the new tables; remove the route rule.
