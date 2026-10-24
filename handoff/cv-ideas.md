# CV layout ideas and a public CV URL (proposal)

Status: proposal only, nothing implemented. Facts are marked as verified in the repo; everything else is labelled **Assumption**.

Verified baseline (from `openspec/changes/export-profile-cv/`, `openspec/specs/`, `k8s/`, code):

- Export is `POST /api/cv/export`, deterministic, `Profile` -> `CvDocument` (`ProseSection`, `EntrySection`, `TagSection`, `LineSection`, `CvEntry` with heading, subheading, period, place, link, text, bullets, tags) -> Markdown/HTML/PDF renderers. Templates are CSS plus a small structure choice; PDF is the HTML run through openhtmltopdf (CSS 2.1: floats, tables, no flexbox/grid). Font: bundled Source Sans 3 (OFL).
- Edge (Traefik) sends `/` to `ui` (nginx) and only `/api` and `/auth` to the Spring gateway; the gateway answers 404 for everything else, adds `nosniff`, `X-Frame-Options`, `Referrer-Policy`, HSTS, and explicitly adds no CSP (`gateway-hardening`). One origin, so no CORS (`edge-routing`).
- Backend: resource server, `anyRequest().authenticated()`, with `permitAll` precedents for `/actuator/health/**`, the OpenAPI paths, and `GET /api/files/*/content` (single-use ticket). `/api/admin/**` is ADMIN only.
- Profile is one JSON document per user, `owner_sub` primary key, `ON DELETE CASCADE` from `app_user`. Liquibase files are `00N-*.sql` in `backend/src/main/resources/db/changelog/` (latest `008-files.sql`).
- Per-user in-memory limiters exist (`AnalysisLimiter`, `ExportLimiter` planned). No per-IP limiter exists yet (**not verified anywhere in the specs; assume none**).

---

# PART A: more layouts

## A.1 Common constraints

All layouts are rendered by the same `HtmlRenderer` + a stylesheet per template, so each new layout is mostly CSS in `Templates.kt` plus a `CvTemplate` enum value. The `CvTemplate` enum is part of the "do not change" Contract of `export-profile-cv`, so new templates go into a **follow-up change** after that one is deployed (additive enum values, request default unchanged).

PDF path rules: floats, tables, `position:absolute/relative`, borders, `page-break-inside: avoid` work. Not usable: flexbox, grid, `border-radius` (silently square), CSS variables, `calc()`. Each layout below states how it copes.

## A.2 Candidates

| # | Name | Suits | Emphasises | Model impact | PDF path | Effort |
|---|---|---|---|---|---|---|
| 1 | **Timeline** | Career with 3 to 8 clear stints, mid to senior | Chronology and progression; period in a left gutter, dot on a vertical rule | None. Uses `EntrySection`; `period` goes in the gutter, `place` after the employer. | Yes: left border + absolutely positioned date and dot (CSS 2.1). Dots are square in PDF (no radius), acceptable. | S |
| 2 | **Developer profile** (skills matrix + tags) | Engineers hiring managers skim for stack fit | A grouped skills table (Languages, Frameworks, Platform) with level dots, tag chips on every entry | **New field needed**: skill groups. `TagSection` is flat today. Add optional `group` to tags or a new `MatrixSection(title, rows: List<MatrixRow(label, tags, level?)>)`. Grouping source: a fixed keyword map in `CvBuilder` (Assumption: skills have no category in `Profile`; verified only `origin`, `sources`, level) | Yes: a table for the matrix, inline-block chips with 1px border. | M |
| 3 | **Portfolio / project-first** | Strong open source record, maintainers | Open source projects before experience: name, stars, language, user note, analysis summary and technologies; experience shortened | None for content. Needs a template-level **section order default** (OPEN_SOURCE after PROFILE). Order already selectable via `sections`, so this is a default only. Optional: richer project card via existing `CvEntry` (`link`, `tags`, `text`). | Yes: bordered blocks, `page-break-inside: avoid`. | S |
| 4 | **Swiss formal** | Applications to banks, insurers, public sector, older-style HR | Conventional Swiss Lebenslauf: label column left, values right, reverse chronological, plain serif | **New fields, all optional and opt-in**: `address`, `nationality`, `dateOfBirth`, `civilStatus`, "references on request". Personal-data heavy and often discouraged now; ship only address and nationality-free default (see D4). Work authorisation already exists. Photo is out of scope in the parent change. | Yes: two-column table, no CSS3. | M |
| 5 | **ATS plain** | Large employers with applicant tracking systems, recruiters' parsers | Maximum machine readability: single column, no tables, no icons, standard headings, no tag chips (skills as a comma line) | None. Skips `TagSection` chip styling, renders tags as text. Differs from CLASSIC mainly by dropping any table or border, headings in the exact words ATS look for. | Yes, simplest. | S |
| 6 | **Long-form (2 pages)** | Senior or lead with 10+ years, consulting profiles | Room for achievements, analysis summaries, publications; running header with name and page number on page 2 | None. Uses `@page` margin boxes (`@bottom-right { content: counter(page) }`) which openhtmltopdf supports (verify, **Assumption**). Page numbering absent in HTML/Markdown. | Yes, if margin boxes work; otherwise a plain footer is skipped. | M |

Notes on CLASSIC overlap: #5 is close to CLASSIC; if CLASSIC is already ATS-safe (it is, per design decision 5), #5 can be dropped and its "comma skills line" folded in as a density option. Recommendation: build 1, 2, 3, 4; skip 5 and 6 (6 becomes a density option, see A.4).

## A.3 Mockups (3 favourites)

Self-contained, no external resources, inline CSS, print-friendly (`@page` A4), invented person only. Open in a browser and print to check.

- `/private/tmp/claude-501/-Users-moldo-Documents-code-projects-job-hunter/6cf7a101-886a-41c1-9bf0-bcabbef38647/scratchpad/cv-mockups/timeline.html` (Timeline, green accent #14532d)
- `/private/tmp/claude-501/-Users-moldo-Documents-code-projects-job-hunter/6cf7a101-886a-41c1-9bf0-bcabbef38647/scratchpad/cv-mockups/developer.html` (Developer profile, slate + sky accent, skills matrix, chips, open source cards)
- `/private/tmp/claude-501/-Users-moldo-Documents-code-projects-job-hunter/6cf7a101-886a-41c1-9bf0-bcabbef38647/scratchpad/cv-mockups/swiss.html` (Swiss formal, German labels, serif, label/value table)

The mockups use `border-radius` for dots and chips; the real stylesheets must drop it for the PDF path (or accept square corners). They use Arial/Georgia fallbacks; the real ones use the bundled Source Sans 3.

## A.4 Cheap theming

| Option | Cost | Notes |
|---|---|---|
| Accent palette | XS | 6 fixed colours (slate, blue, green, teal, burgundy, graphite) chosen to keep contrast 4.5:1 on white. Applied to headings, rules, chips. MODERN already uses "one accent from a fixed palette"; generalise to all templates. Fixed list, never a free colour input (no CSS injection, predictable print). |
| Font pairing | S | Open-licensed (OFL) sets that cover German and French: Source Sans 3 (default, bundled), Inter, IBM Plex Sans + IBM Plex Serif, Lato, Libre Baskerville for headings. Each family adds about 1 MB per four styles to the image. Offer at most 2 extra families: **IBM Plex Sans/Serif** and **Inter**. Fonts must be bundled (PDF cannot fetch). For the downloaded HTML, fall back to system fonts when the viewer lacks them. |
| Density | XS | `COMFORTABLE` / `COMPACT` / `TIGHT`: base size 10.5/10/9 pt and spacing multipliers. Replaces the need for a separate 2-page layout. |
| Page size | XS | A4 only (Swiss default); Letter not needed. |

Per-user settings (stored on the profile or a small settings JSON, applied to every export and the public page): accent, font pairing, language of the public page, default template, section order. These express identity.

Per-export options (request body, not stored): format, template override, language override, section selection, include analysis, include work authorisation, density for a one-off "fit to one page". These are situational.

Rule: whatever is stored must be exposed to deletion/export with the profile (same JSON document, **Assumption**: stored as `profile.cvSettings`, lenient read like `contact`, no schema change).

---

# PART B: public CV URL

## B.1 Opt-in model (question 1)

| Aspect | Design |
|---|---|
| Default | **Off.** No row, nothing served, no URL exists. |
| Enable | User opens "Public CV" page, picks template, language, what to show, sees the preview, presses **Publish**. |
| Revoke | **Unpublish** deletes the snapshot and the token at once; the URL answers 404 (not 410: do not confirm it ever existed). |
| Rotate | **New link** generates a new token, invalidates the old one atomically. Needed if a link leaked. |
| Expiry | Optional "expires after 30/90/365 days / never"; default 90 days with a reminder in the UI (**Assumption**: no email infra verified, so reminder is in-app only). Expiry stops the link outliving the user's intent. |

## B.2 URL scheme (question 2)

| Scheme | Pros | Cons |
|---|---|---|
| A. Random token `/cv/k3J9...` (128-bit, base64url, 22 chars) | Unguessable, no collisions, no squatting, no enumeration, no reserved words, rotation trivial | Ugly, not memorable |
| B. Handle `/cv/ada-example` | Pretty on a business card | Squatting, impersonation (someone takes `/cv/ada-example` for a real person), enumeration of users, collisions, reserved words (`admin`, `api`, `auth`, `new`...), handle recycling after deletion, moderation burden |
| C. Handle + token `/cv/ada-example-k3J9x` | Readable and unguessable | Still needs slug rules; the slug is decoration and user-chosen text appears in URLs (doxxing of name) |

**Recommendation: A, random token, 128 bits from `SecureRandom`, stored as SHA-256 hash** (so a database leak does not yield live links; the token is shown once on publish). Optional later: a display vanity redirect, only if the owner asks. If B is ever added: lowercase `[a-z0-9-]{3,30}`, reserved-word list, no recycling for 12 months, unique index, rate-limited changes.

Where the URL lives (verified constraint: the edge and gateway only forward `/api` and `/auth`; everything else goes to nginx which serves `index.html`):

| Option | Change needed | Verdict |
|---|---|---|
| `https://s4v3.net/api/public/cv/{token}` serves server-rendered HTML directly from backend | None at edge or gateway; one backend `permitAll` matcher | **Recommended for v1.** Works for crawlers and Open Graph unfurlers (they do not run JS). Slightly long URL. |
| `https://s4v3.net/cv/{token}` (React route that fetches JSON) | None, nginx fallback serves `index.html` | Rejected: needs JS, OG tags impossible without server rendering, UI CSP blocks inline styles of a rendered CV. |
| `https://s4v3.net/cv/{token}` proxied by edge to backend | New edge HTTPRoute rule + gateway route + update `edge-routing`, `gateway-routing` ("unknown paths 404" scenarios) | Nicer URL; do as v2 if wanted (S effort, three spec deltas). |

## B.3 What is shown (question 3)

Public page is built by the same `CvBuilder` with a fixed, stricter option set. Per-field control lives in a `PublicCvSettings` object saved with the publication.

| Data | Default | User control |
|---|---|---|
| Full name, headline | Shown (the CV is useless without) | Name can be reduced to "Ada E." |
| Summary, experience, education, skills, languages, publications | Shown | Per-section toggle (same `CvSectionKind` list) |
| Open source projects | Shown without analysis | Toggle; analysis summary is a separate opt-in |
| Email | **Hidden** | Option "show", or "contact form" (see below) |
| Phone | **Hidden** | Option "show" |
| Website, LinkedIn, GitHub links | Shown (already public by nature) | Per-link toggle |
| Location | City only, no street | Toggle |
| Work authorisation | **Hidden** | Option (as in export) |
| Preferences, salary, cantons, target roles, workload | **Never**, not even as options | None. Same rule as export; enforced by building the document from the shared builder whose output never contains them, plus a test. |
| Private notes, API keys, files, posting/match data | Never | None |

Contact-via-form: **do not build in v1** (needs spam control, mail sending, abuse handling; **Assumption**: no outbound mail service verified for the backend). Offer "show email" or a LinkedIn link instead.

Preview: the Publish page renders exactly the stored snapshot HTML in a sandboxed iframe (same mechanism as the export preview), above the Publish button, headed "This is exactly what anyone with the link will see". With a snapshot design (B.5) preview == published bytes by construction.

## B.4 Fit with the current security setup (question 4)

| Concern | Design |
|---|---|
| Path | `GET /api/public/cv/{token}` (HTML), `GET /api/public/cv/{token}/pdf`, `GET /api/public/cv/{token}/og.png` (optional). All GET only. |
| Backend rule | Add one matcher before `anyRequest().authenticated()` in `SecurityConfig`: `requestMatchers(GET, "/api/public/cv/*", "/api/public/cv/*/pdf", "/api/public/cv/*/og.png").permitAll()`. Nothing else under `/api/public/**` is open (explicit patterns, not `/**`). Publish management endpoints stay authenticated: `/api/cv/public` (GET status, PUT publish, DELETE unpublish, POST rotate). Same pattern as `/api/files/*/content`. |
| Gateway | No change: it forwards `/api/` unchanged and does not require tokens (`gateway-routing` "Pass-through"). It already adds nosniff, X-Frame-Options (fine: page is not to be framed), Referrer-Policy, HSTS. It adds no CSP, so the **backend sets its own CSP**: `default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`. |
| Edge | No change for the `/api/public` option. |
| CORS | None needed (same origin, GET of a document). Send no `Access-Control-Allow-Origin`. |
| Rate limiting | New per-IP limiter in the backend (copy `AnalysisLimiter` pattern, in-memory): e.g. 60 requests per minute per IP, then 429 with `Retry-After`; and a stricter 10 per minute for unknown tokens (anti-guessing). **Assumption** about client IP: take it from `X-Forwarded-For` set by Traefik; must verify the gateway/backend forwarded-header handling and trusted-proxy config before relying on it, else everyone shares the gateway's IP. Defence in depth: Traefik rate-limit middleware (not currently in `k8s/`, would be a new manifest). |
| Caching | `ETag` = hash of the snapshot, `Last-Modified` = published time, `Cache-Control: public, max-age=300` only if indexable; default `private, max-age=0, must-revalidate`? Recommendation: `Cache-Control: no-cache` with ETag + 304, so revoke takes effect within one request and shared caches (CDN, none today) cannot keep it. Never `immutable`. |
| Indexing | Default `X-Robots-Tag: noindex, nofollow, noarchive, nosnippet` and `<meta name="robots">`. Opt-in "allow search engines" switches to `index, follow` and also needs the page to be reachable by crawlers: `robots.txt` at the origin is served by nginx and cannot see backend state; keep `robots.txt` allowing `/api/public/cv/` and rely on the header. |
| Referrer | Existing `Referrer-Policy` from the gateway; also `<meta name="referrer" content="no-referrer">` in the page. Links out use `rel="noopener noreferrer nofollow"`. |
| Logging | Never log the token, the IP together with the token, or any profile text. Log only: status, snapshot id hash prefix (8 chars), duration. Access logs at gateway already exclude query strings; the token is in the **path**, so confirm Traefik/gateway/backend access logs do not print paths for this route (**Assumption: Traefik access log not verified**); if they do, use a short `X-Request-Id` instead and mask `/api/public/cv/**` in log config. |
| Errors | Unknown, revoked, expired all return the same 404 body and timing. |
| Container | No change: backend container hardening is already in `workload-deployment`; network policies allow gateway -> backend already. |

## B.5 Snapshot vs live (question 5)

| | Snapshot (frozen on Publish) | Live render |
|---|---|---|
| Preview == public | Exactly | Only if the user re-checks |
| Edit leaks | None; later profile edits stay private until re-publish | Every edit is public instantly (a half-edited phone number) |
| Deletion | Delete row | Nothing stored, but data is exposed as long as the flag is on |
| Staleness | User must re-publish (UI shows "profile changed since publish") | Always current |
| Storage | One HTML blob (about 30 KB) + settings | Settings only |
| Cost per request | Serve bytes, ETag trivial | Render per hit (CPU, DoS) |

**Recommendation: snapshot.** Store the rendered HTML (and optionally PDF generated on first request and cached by snapshot id). The page is then safe to serve at high rate, ETag is the snapshot hash, and "what is public" is auditable.

Data model (Liquibase `009-public-cv.sql`, **Assumption** on exact names):

```
public_cv (
  owner_sub        TEXT PRIMARY KEY REFERENCES app_user(sub) ON DELETE CASCADE,
  token_hash       BYTEA NOT NULL UNIQUE,      -- SHA-256 of the token
  settings         JSONB NOT NULL,             -- template, language, toggles, indexable
  html             TEXT  NOT NULL,             -- frozen, already escaped, CSP-safe
  etag             TEXT  NOT NULL,
  published_at     TIMESTAMPTZ NOT NULL,
  expires_at       TIMESTAMPTZ,
  version          INT NOT NULL DEFAULT 1
)
public_cv_audit (
  id BIGSERIAL PRIMARY KEY,
  owner_sub TEXT NOT NULL REFERENCES app_user(sub) ON DELETE CASCADE,
  event TEXT NOT NULL,                         -- PUBLISHED, ROTATED, REVOKED, EXPIRED
  at TIMESTAMPTZ NOT NULL,
  settings_summary JSONB                       -- which fields were public, no values
)
```

One public CV per user in v1 (primary key = `owner_sub`). No view counter in v1 (a counter is tracking of visitors; skip).

nDSG / GDPR consequences:

| Topic | Handling |
|---|---|
| Legal basis | Consent by explicit action (Publish), withdrawable (Unpublish). |
| Account deletion | `ON DELETE CASCADE` removes snapshot and audit; URL answers 404 immediately. Test: delete account, request URL, expect 404. |
| Data export | Include `public_cv` settings, publication times and audit in the user's data export (current export spec for accounts not read; **Assumption** one exists per the context in `openspec/config.yaml`). Include the published HTML. |
| Retention | Snapshot lives until unpublish, expiry or deletion. Expired rows purged by a scheduled job after 30 days; audit kept while the account exists. |
| Audit | `published_at` and the audit table answer "when was this made public and what was shown". |
| Third parties | Search engines and archive sites may copy it: cannot be recalled; this is stated in the Publish dialog, and `noindex` default reduces it. |
| Processor docs | Add a note in `docs/` on the new public processing purpose. |

## B.6 Abuse and privacy (question 6) and threat model

| Threat | Impact | Mitigation | Residual |
|---|---|---|---|
| Token guessing / enumeration | Reading others' CVs | 128-bit random token, stored hashed, per-IP limit with tighter limit on 404s, uniform 404 | Negligible |
| Scraping a known link | Mass copy | Per-IP limit, noindex, no listing/search endpoint, no sitemap | A determined scraper with a link; inherent |
| Doxxing (email, phone, address, employer chain) | Harassment | Contact hidden by default, no street address, no date of birth by default, preview, clear warning | User choice |
| Link outlives intent (shared in an old email) | Stale exposure | Expiry default 90 d, revoke, rotate, "last published" shown in UI | Copies already taken |
| Search-engine cache | Persists after revoke | `noindex, noarchive` default; indexing opt-in with a warning that removal depends on the engine | Real for opt-in users |
| Social unfurlers (Slack, LinkedIn) | Preview exposes name/headline | OG tags show only name + headline + generic line, never email/phone; OG off by default | Low |
| Stored XSS via profile text | Script in a page on s4v3.net origin | Single `esc()` already in renderer, CSP `default-src 'none'`, no JS in page, snapshot built server side; the public page is on the **same origin as the app** so a bug would reach the UI's token storage. **Mitigation: strong CSP plus no scripts; consider the `Content-Security-Policy: sandbox` directive** so the document gets an opaque origin | Low with CSP sandbox |
| Impersonation (someone publishes another person's CV) | Reputation harm | Only own profile can be published (by `sub`); no handles; no public directory | Low |
| Takedown request | Legal | Admin can revoke any link (`/api/admin/**`, ADMIN) | Needs admin UI later |
| DoS via public endpoint | CPU, DB | Snapshot serving only; PDF generated once per snapshot, cached, semaphore shared with export | Low |
| Token in logs/Referer | Link leak | No path logging, `no-referrer`, outbound links `noreferrer` | Verify Traefik log |
| Profile text in logs | PII | Nothing logged from content (existing rule) | |

Explicitly not to build: a public directory or search of CVs, handles/vanity URLs in v1, visitor analytics or view counters, contact form (v1), comments, "recruiter access" accounts, embedding in iframes or widgets, custom domains, CORS/JSON public API, live rendering, public photo, public salary or preferences in any form.

## B.7 Extras (question 7)

| Extra | Design | Default |
|---|---|---|
| PDF download link | `GET /api/public/cv/{token}/pdf`, rendered once from the snapshot HTML with the existing `PdfRenderer`, cached per snapshot (bytes in the same table or `public_cv_pdf`), same limits and semaphore | Off, a checkbox "allow PDF download" |
| Open Graph card | `og:title` name, `og:description` headline + city, `og:type=profile`, `og:url`; no image in v1 (an image would need a renderer and invites previews of faces/contacts). `twitter:card=summary`. Tags in the page head only if `allowSocialPreview` | Off |
| Language switch | Snapshot has one language; offering both doubles storage. Defer. | |

CSP caveat: OG tags are meta, unaffected by CSP.

## B.8 Modules, specs, change split (question 8)

Ownership: **Assumption** (Modulith rules per `openspec/config.yaml`: modules interact only through public API or events, `ApplicationModules.verify()` must pass; `cv` may call the public `ProfileService`, `profile` must not import `cv`).

Recommended: a **`publicprofile` package inside the existing `cv` module** (`net.s4v3.backend.cv.publiccv` public API: `PublicCvService`; `internal/` controllers, repository, limiter). Reasons: it reuses `CvBuilder` and `HtmlRenderer` directly (both in `cv.export`); a new top-level module would force these to become public API of `cv`. Account deletion needs no event because of `ON DELETE CASCADE` on `app_user`. Alternative: its own module `publiccv` depending on `cv`'s public renderer API; cleaner isolation of the unauthenticated surface, costs making `CvBuilder`/`HtmlRenderer` public. I prefer the in-module package for v1.

Specs: new capability `cv-public` (endpoints, snapshot, tokens, headers, limits, deletion, audit), new `ui-cv-public` (Publish page), modified `accounts` (deletion removes public CV, if its spec lists deleted data), modified `cv-export` (builder takes a `PublicProjection` of options, only if needed). Also a delta on `ui-delivery` is not needed. If route v2 is chosen: `edge-routing`, `gateway-routing`.

### Change 1: `public-cv-page` (core)

| Group | Tasks |
|---|---|
| Schema | `009-public-cv.sql` (tables above), Liquibase master entry |
| Backend | `PublicCvService` (publish, status, unpublish, rotate), token generator + hash, `PublicCvController` (authenticated management) and `PublicCvPageController` (public GET), snapshot via `CvBuilder`+`HtmlRenderer` with a public projection that never contains contact unless enabled, `SecurityConfig` matcher, headers (CSP, robots, ETag, 304), `PublicIpLimiter`, expiry job, audit writes |
| Tests | publish/revoke/rotate; another user cannot manage mine; anonymous page 200 with snapshot, wrong token 404 same body; revoked 404; expired 404; deletion cascade; email/phone absent by default; preferences never present (fixture contains salary); XSS fixtures; headers present; per-IP 429; no profile text or token in captured logs; Modulith verify |
| UI | `/publish` page: settings, preview iframe, Publish/Unpublish/New link, copy-link button, warning text; route and shell link |
| Docs | `backend/README`, `ui/README`, `docs/cv-public.md` (privacy statement) |

Effort: M-L (about 3 to 4 days of agent work plus review and prod test on s4v3.net).

### Change 2: `public-cv-extras`
PDF download, OG tags, indexable opt-in, per-field overrides for name abbreviation. Effort S-M.

### Change 3 (optional): `public-cv-route` (`/cv/{token}` via edge) and `cv-layouts-2` (Part A templates, 1 to 2 days for four CSS templates plus matrix model change). Effort S each (route), M (layouts).

## B.9 Questions for the owner (with recommended defaults)

| # | Question | Recommended default |
|---|---|---|
| 1 | Public CV off by default and opt-in per user? | Yes, off |
| 2 | Random token URL or user handle? | Random 128-bit token, hashed in DB; no handles in v1 |
| 3 | Where does the URL live: `/api/public/cv/{token}` now, or `/cv/{token}` with edge change? | `/api/public/cv/{token}` first, pretty route later |
| 4 | Show email and phone? | Hidden by default, user may show; no contact form in v1 |
| 5 | Work authorisation and analysis summaries public? | Both off, opt-in each; preferences/salary never |
| 6 | Snapshot or live? | Snapshot (stored HTML), explicit Publish |
| 7 | Default expiry? | 90 days, extend in one click, "never" allowed |
| 8 | Search engines? | `noindex` default, opt-in |
| 9 | PDF and Open Graph in v1? | No; second change |
| 10 | Owner of the code? | `publiccv` package inside `cv` |
| 11 | Per-IP limiting: backend in-memory only, or also a Traefik middleware? | Backend only in v1 after verifying how the client IP arrives |
| 12 | Which Part A layouts? | Timeline, Developer profile, Portfolio, Swiss formal (skip ATS-plain and 2-page; density option instead); Swiss formal without date of birth/civil status by default |
| 13 | Theming | Fixed accent palette + 2 extra font families + density; accent, font, default template stored per user; rest per export |
