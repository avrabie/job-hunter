# Public CV page: security and privacy review (read-only)

Scope read: openspec change, `cv/publiccv/**`, the three controllers, `CvBuilder`, `HtmlRenderer`, `Html.kt`, `Templates.kt`,
`SecurityConfig`, `009-public-cv.sql`, `k8s/base/edge.yaml`, `ui/src/publiccv/` (only grepped for href/innerHTML/window.open).
Paths below are relative to `backend/src/main/kotlin/net/s4v3/backend/` unless noted. No source changed. No tests run.

## Findings

### M1 (medium) Stale cached page after delete-and-recreate shows settings the owner switched off
`cv/publiccv/internal/DefaultPublicCvService.kt:86` (version) with `:128` (cache key) and `RenderCache.kt:10-17`.
`settingsVersion` restarts at 1 when the row is recreated, and `RenderKey` has no settings content or row id.
Exploit: owner publishes with email on, a visitor loads it (v1 cached, 10 min TTL), owner DELETEs and re-PUTs with email off:
same sub, same profile version, same settingsVersion 1, so the cache returns the old page with the email for up to 10 min.
The same happens for two concurrent saves that both compute `existing.settingsVersion + 1` (lines 79-86, no transaction).
Fix: put the settings themselves (a data class, already `equals`-able) or `updatedAt` in `RenderKey`, or keep a version that never resets
(e.g. a global sequence column). Also call `cache` invalidation by sub in `save` and `remove` (cheap, belt and braces).

### M2 (medium) 30-day handle-change rule is bypassed by delete then claim
`DefaultPublicCvService.kt:73` (`changed = existing != null && ...`), `:111-119`, `PublicCvRepository.kt:123-125` (comment promises the opposite).
Exploit: DELETE /api/cv/public, then PUT a new handle: `existing == null`, so no `HANDLE_CHANGED` audit and no 30-day check. Repeat
without limit: each cycle parks the old handle in a 90-day hold, so one user can squat any number of handles (brand names, other
people's names) for 90 days and bloat `public_cv_handle_hold`. Neither DELETE nor PUT has a rate limit.
Fix: when `existing == null`, treat the claim as a change if the owner has an active hold or a `REMOVED`/`HANDLE_CHANGED` audit row in the
last 30 days for a different handle (apply `ChangeTooSoon`, write `HANDLE_CHANGED`); add a per-sub limiter (e.g. 20 saves/hour) on PUT/DELETE
and a cap on active holds per sub (e.g. 3).

### M3 (medium) Handle existence oracle that ignores the "uniform 404" goal, unlimited
`DefaultPublicCvService.kt:68-71` and `:76`, `PublicCvController.put`.
A user who already has a page can PUT `published:false` with any candidate handle: `409 handle_taken` or `handle_held` means the handle belongs
to someone (published, paused, locked or held), `409 handle_change_too_soon` means free (Taken is checked before ChangeTooSoon). No limiter on PUT.
`GET /api/cv/public/handle` gives the same answer, limited to 60/hour/sub, but registration is open so accounts are cheap.
This reveals paused/unpublished pages and recently removed handles, which the public endpoint hides by design.
Fix: rate-limit PUT like the checks (same limiter or 60/h); decide whether "taken" for unpublished pages is acceptable and write it into the spec
(the UX needs some taken signal, so mitigation is the rate limit, not removal).

### M4 (medium) Limiter map growth: attacker-chosen keys make every request do an O(n) sweep under one lock
`WindowLimiter.kt:22-25,47-50`, key source `PublicCvPageController.kt` `clientIp` (1-45 chars, any text) and plausible handles (`PLAUSIBLE`).
Exploit: if the edge ever passes a client-chosen first `X-Forwarded-For` (see U1), send >10,000 distinct values within one minute:
`calls.size > SWEEP_AT` stays true (live keys are not removed by the sweep), so each later call to `pageByIp`/`notFoundByIp` runs a full
sweep inside `@Synchronized`, serialising all public traffic (CPU DoS) and growing memory by the request rate times 60 s.
Without XFF spoofing the same holds with many real IPs, but is harder. Per-handle keys are bounded by 36^30 plausible junk values that
pass `PLAUSIBLE` and each costs a DB lookup first (pageByIp limits that to 60/min per IP).
Fix: sweep at most once per window (time-based, `lastSweep`), hard cap the map (e.g. 50k, then fail closed or evict oldest),
parse the XFF value with a strict IP parser and key IPv6 by /64.

### L1 (low) HEAD is not open: link previews and crawlers get 401
`SecurityConfig.kt:50` uses `HttpMethod.GET` only; Spring MVC would serve HEAD, but Security answers 401. Not a leak, a function gap.
Fix: add `HttpMethod.HEAD` to the matcher (and ensure the HEAD path does not charge a different limiter).

### L2 (low) 404 and 429 do not carry the CSP; the 404 body is JSON
`PublicCvPageController.kt` `denyHeaders()` (about line 117) has nosniff, no-store, noindex, referrer but no CSP. Harmless (JSON, nosniff), but the
spec text for the page requires CSP only on served pages, so this is a spec reading to confirm, not a defect. Fix if wanted: add the same CSP.
Also content negotiation: a request with `Accept: application/json` only gets 406 for an existing page and 404 for others; it shows only
what a normal GET shows (published or not), so no extra leak.

### L3 (low) Edge: `/cv` and `/cv/a/b` become backend 401, not 404 as the YAML comment says
`k8s/base/edge.yaml:58-59`. PathPrefix `/cv` matches `/cv`, `/cv/`, `/cv/a/b` (not `/cvx`). ReplacePrefixMatch gives `/api/public/cv`, `/api/public/cv/`,
`/api/public/cv/a/b`, and `/cv/x?y=z` becomes `/api/public/cv/x?y=z` (query kept, ignored). Only `/api/public/cv/<one segment>` is permitAll,
so the others get 401 from `anyRequest().authenticated()` (a bare JSON 401 in the browser). Nothing else under `/api` is reachable, since the prefix is fixed.
Unverified: whether Traefik normalises `..` and `%2e%2e` before matching; the backend's StrictHttpFirewall rejects `..`, `%2f`, `;`, `%25` (report g4 saw 400),
which would stop `/cv/../../files/x/content` (the only other public GET). Fix: update the comment; optionally permit `/api/public/cv/**` GET and let the
controller answer 404, or leave as is.

### L4 (low) Content in the page that the owner may not expect
`CvBuilder.kt:56-61,111-117,130,146`. Verified against the settings flags, nothing prints without its flag: email/phone/website/linkedin/codeAccounts/location/headline/
workAuthorisation/analysis are all gated; preferences, salary, cantons, target roles, skill sources and analysis outside OPEN_SOURCE are never read.
But these print whenever their section is ticked and have no separate flag: experience `location` (job place), open source `note` (field doc says "the user's own text",
`profile/Profile.kt:83`), project `url` and `fullName`, publication `url` (link target), and free text (`summary`, achievements, notes, description) is printed
verbatim, so an email or phone typed into a summary leaks despite the contact flags. Education/summary are inside their sections as designed.
Fix: UI warning text on the section toggles (notes, summary, job place); optionally a `notes` flag, default off. Do not scrub free text.

### L5 (low) Handle normalisation accepts one non-ASCII input
`HandleRules.kt:28`: `lowercase()` maps U+212A (Kelvin sign) to `k`, so `/cv/%E2%84%AAevin` and a PUT of that handle resolve to `kevin`. No homoglyph risk: the stored and checked form is
the normalised ASCII, and the regex `[a-z0-9]` is applied after. Other cases (`İ` becomes `i` + combining dot) fail the regex. Confirmed charset is ASCII-only after normalisation.
Reserved list covers the UI routes (`profile, postings, matches, export, publish, admin, register, callback`) and API words; it lacks impersonation words
(`security, abuse, jobs, hr, info, noreply, webmaster, postmaster`). Fix: use `lowercase(Locale.ROOT)` plus an ASCII check on the raw input; extend the list.

### L6 (low) Non-atomic save and remove
`DefaultPublicCvService.kt:90-97,116-118`: upsert, hold insert and audit are separate statements, no `@Transactional`. Between the upsert that releases handle A and
`insertHold(A)`, another user can claim A, or a crash loses the hold and the audit row (the 30-day rule reads the audit). A unique violation on `lower(handle)` is caught and mapped to
409 `handle_taken` (good), but any other `DataIntegrityViolationException` is also reported as taken. Fix: `@Transactional` on `save`/`remove`, hold insert before releasing.

### L7 (low) Preview ticket scan and deleted-user path
`PreviewTickets.kt:37-47`: `redeem` scans the whole map each call; reachable unauthenticated with any 32-hex string (no IP limit). Tickets are only 30/hour/user, so small. Unverified: what
`profiles.get(sub)` does for a user deleted within the 60 s (500 instead of 404). Fix: expire lazily on the looked-up ticket, and catch not-found in `renderPreview`.

### Info
- U1 (unverified): client IP trust. `traefik-values.yaml` sets no `forwardedHeaders`, so Traefik should drop a client-supplied `X-Forwarded-For` and add the real one; the gateway trusts every
  caller (`k8s/base/gateway.yaml:56`), appends the edge pod, and the backend takes the first value. If hostPort traffic is SNATted, all clients share one IP (the 10/min 404 limit then hits everyone).
  Test on prod with a forged header. If it is spoofable: per-IP limits are void, per-handle (600/min) still caps a single page, but lets anyone make one handle answer 429 to others (cheap DoS of one page).
- Impersonation: any user can publish a page named like a real person; takedown exists. Control and bidi characters in names are not stripped (`Html.kt`).
- Page controller logs `status`, `ms`, 8 chars of the ETag per request; no handle, IP or ticket. Grep of the new code found no other log or exception text with handle, IP, settings or profile text
  (`PublicCvMaintenance` logs a count and the exception class name).

## Checked and fine
- XSS: every string goes through `esc` (`& < > " '`); attribute values are double-quoted; `isWebUrl` allows only http(s) without whitespace; the `rel` replace only matches anchors from `link()`
  (text `<` is escaped, so profile text cannot contain `<a href="`); `<title>` is escaped; `lang` comes from an enum; head extras and CSS are constants; robots content is from a boolean. CSP is exactly the specified string
  (includes `sandbox`) on served and 304 answers, plus a meta CSP in the page.
- Access: `/api/public/cv/*` and `/api/cv/public/preview/*` are GET single-segment matchers; `/x/`, `/x/y`, double slash, `;`, `%2e`, `%25` end in 401 or the firewall's 400 independent of the handle; POST/PUT/DELETE/OPTIONS there are 401.
  Owner endpoints use `currentUser.id()` only, bodies with a user id are refused (unknown field), no path takes a handle or sub. `/api/admin/**` is `hasRole("ADMIN")` by prefix.
- Preview ticket: 128 bits from `SecureRandom`, atomic `remove` (one use, an attempt consumes it), 60 s, bound to sub and settings, profile read for the bound sub, never indexable, not cached, hex format checked before lookup.
- 404 body and headers are uniform for unknown, paused, locked, removed, no-name and junk handles; limiter order does not make hit/miss differ beyond published or not.
- Privacy: audit rows hold flag names and booleans, no handle; all three tables `ON DELETE CASCADE` to `app_user`; no profile copy stored (only settings flags and section names); renderPublic checks row, published and lock before the cache.
- Unique index `public_cv_handle_lower_idx` plus `CHECK (handle = lower(handle))` makes simultaneous claims safe; violation maps to 409. Lock cannot be cleared by owner save (`upsert` omits `locked`), by remove (409 `locked`), or by changing the handle (the row keeps `locked`).

## Done well
Single escape function and a tight, tested CSP with sandbox; strict JSON parsing that drops exception text; limiter and ticket classes small with injectable clock; settings default to off and sections to empty;
flags-only audit; ADMIN protected by prefix so new admin endpoints are closed by default.

## Summary
1. High: 0
2. Medium: 4 (M1 stale cache after recreate, M2 30-day bypass and handle squatting, M3 handle oracle on PUT, M4 limiter sweep/growth)
3. Low: 7 (L1 to L7), plus info items U1 and others
4. Unverified: Traefik XFF handling and `..` normalisation, `profiles.get` for a deleted sub, CSP surviving the Spring gateway, HEAD behaviour on prod
5. Top fix 1: add settings (or updatedAt) to `RenderKey` and invalidate the cache on save and remove (M1, privacy leak)
6. Top fix 2: close the delete-then-claim path: enforce the 30-day rule when an owner re-claims within 30 days, rate-limit PUT and DELETE, cap holds (M2, M3)
7. Top fix 3: time-based sweep and a hard cap in `WindowLimiter`, strict IP parsing for keys (M4), then test a forged `X-Forwarded-For` on prod (U1)
8. Also quick wins: `@Transactional` on save/remove, `Locale.ROOT` in `normalize`, HEAD matcher, fix the edge comment
9. No XSS, IDOR, ticket or settings-flag leak found in the render path
10. Report file: `handoff/pcv-review.report.md`
