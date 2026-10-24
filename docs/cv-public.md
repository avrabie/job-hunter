# Public CV page

For the owner (how the page is published, paused and removed) and for administrators (how a page is taken down and unlocked).
The behaviour is specified in `openspec/changes/public-cv-page/specs/` until the change is archived. The endpoints, settings,
limits and config keys are in `backend/README.md` ("Public CV"); the page and its controls are in `ui/README.md` ("Public CV page").

## What is public, and what never is

The owner chooses a handle (for example `ada-example`). The page is then at `https://s4v3.net/cv/ada-example`, and the direct
address `https://s4v3.net/api/public/cv/ada-example` works too. Nobody needs an account to read it.

- **Always shown once the page is published:** the full name (the page cannot be made without it).
- **Shown only when ticked:** each section (experience, open source projects, education, skills, languages, publications, profile
  summary), the headline, the analysis summaries, the work authorisation, the location, each contact field (email, phone, website,
  LinkedIn, code-account links).
- **Never shown:** preferences (target roles, workload, cantons, salary), anything not ticked, the file cabinet and the stored CV
  file. The page is rendered from the profile by the same builder as the CV export, so an unticked field is not in the HTML at all.
- **Search engines:** the page asks them not to index or archive it (`noindex, nofollow, noarchive, nosnippet`). The owner can
  allow indexing with the "Allow search engines" tick; it is off by default.

## For the owner

1. Open **Public CV** in the menu (`/publish`). Choose a handle, the template and language, and tick what may be public.
2. **Preview** opens what visitors would see, in a new tab, from the saved profile and the settings on the page. It is valid once,
   for 60 seconds.
3. **Publish** makes the page live. **Save** keeps the settings without changing whether it is live.
4. **Pause** takes the page down at once (visitors get the same 404 as for a handle that does not exist). The settings and the
   handle are kept, and **Publish** brings it back.
5. **Remove** deletes the settings and the page. The handle is kept for you for 90 days.

### The live page

The page is live: it is rendered from your saved profile, not from a copy taken when you publish. A profile change you save
shows on the public page at once, in the fields you ticked. The profile page and the Public CV page say so. The trade-off:
a half-finished edit that you save is visible to visitors straight away. To stop that, **Pause** first, edit, and publish again
(the page is not served while paused). A snapshot mode, where the page shows the profile as it was at publishing, is a possible
later option; it is not built.

The page is not cached in a way that outlives a pause: a paused, removed or locked page is answered as not found on the next request.

### Handle policy

- 3 to 30 characters: lower-case letters and digits, with single hyphens between them. Not only digits. Not starting or ending
  with a hyphen. Input in capitals is lower-cased.
- Reserved words are refused, for example `admin`, `api`, `cv`, `profile`, `publish`, `public`, `support`, `s4v3`. The full list
  is `HandleRules.RESERVED` in the backend.
- One handle per user. A handle is unique, whatever its capitals.
- You can change the handle once every 30 days. The first choice of a handle does not count.
- A handle you give up (by changing it or removing the page) is held for 90 days: nobody else can claim it, and you can take it
  back. After 90 days it is free. The owner's own hold is removed when they take it back.
- A handle is a public name. Do not use one that reveals more than you want the world to read.

## For administrators

Use this when a page must come down: abuse, a legal request, or a page that impersonates someone. Takedown does not delete the
profile or the settings; it pauses the page and **locks** it, so the owner cannot publish it again.

Replace the placeholders. Never paste a real token or handle into a shared document, a ticket or a log.

```sh
# Take the page down and lock it (the owner can no longer publish it)
curl -si -X DELETE \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" \
  https://s4v3.net/api/admin/public-cv/<handle>

# Lift the lock (the page stays paused until the owner publishes it again)
curl -si -X POST \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" \
  https://s4v3.net/api/admin/public-cv/<handle>/unlock

# Check what visitors see (expect 404 with {"error":"not_found"} while locked or paused)
curl -si https://s4v3.net/api/public/cv/<handle>
```

Answers: 204 on success; 404 `{"error":"not_found"}` when no page has the handle; 403 for a token without the ADMIN role. The lock
also blocks the owner's **Remove**, which answers 409 `locked`. The takedown and the unlock are written to the audit trail
(`TAKEN_DOWN`, `UNLOCKED`), with the owner's account and no handle.

## Retention and deletion

Three tables hold the data, each referencing the user account with `ON DELETE CASCADE`:

- `public_cv`: the handle, the settings (flags and section names, never the profile), whether the page is published and whether
  it is locked.
- `public_cv_handle_hold`: the released handles, until they expire.
- `public_cv_audit`: what the owner did (see below).

**Deleting the account** removes all three at once, so the page goes at once. The holds go too, so the handle is free straight
away rather than after 90 days. Expired holds are removed by a daily job (03:45, one query).

Copies that search engines or archives have already made cannot be recalled by the system. The rendered pages and preview tickets
are held in memory only, for at most 10 minutes and 60 seconds, and are lost on restart.

## Audit trail

`public_cv_audit` records, per owner: `PUBLISHED`, `PAUSED`, `SETTINGS_CHANGED`, `HANDLE_CHANGED`, `REMOVED`, `TAKEN_DOWN`,
`UNLOCKED`, with a time and the **flags** of the settings: which items are on (for example `contact.email` or `section.SKILLS`), never
the values, and never the handle. The audit is also where the 30-day change rule is read from. It is deleted with the account.

## Threat model (condensed from `design.md`)

| Threat | What is done |
|---|---|
| Guessing or listing handles | Every state that is not served gives the same 404; no list or search endpoint; stricter limit on 404s; a limit per handle |
| Squatting or impersonating a real name | Reserved words; one handle per user; 90-day hold of released handles; takedown and lock by an administrator |
| Taking over an old shared link | The 90-day hold: nobody else can claim a released handle for that time |
| Doxxing through contact details or the employer chain | Everything off by default and per field; preview before publishing; a banner that the page is live; preferences never printed |
| A half-finished edit becomes public | Live by design: Pause, the banner, the audit; a snapshot mode can be added later |
| Stored XSS from profile text | One escaping function; no script in the page; a CSP with `default-src 'none'` and `sandbox`; `nosniff`; http(s) links only, with `noopener noreferrer nofollow` |
| Search-engine and archive copies | `noindex, noarchive` by default; indexing is an explicit option; copies cannot be recalled |
| Scraping a known handle | Limits per IP and per handle; `noindex` (scraping a public page cannot be prevented) |
| Denial of service through rendering | Cache by version; limits per handle; HTML only (no PDF rendering on this path) |
| Handles or content in logs | No content or handle logging; the edge access log is disabled; only status, duration and an ETag prefix are logged |
| A spoofed `X-Forwarded-For` evades the IP limit | The per-handle limit and the 404 limit are the backstop |
| The account is deleted but the page stays | Foreign keys cascade; the page is not found after deletion |

## The edge route `/cv/<handle>`

`k8s/base/edge.yaml` has one rule on the `gateway` route: a path prefix `/cv` is rewritten (`ReplacePrefixMatch`) to
`/api/public/cv`, so `https://s4v3.net/cv/<handle>` is answered by the backend, with the backend's own headers, not by the UI.
The UI has no `/cv/:handle` route. The rewrite is not yet confirmed on the cluster: check it with the requests listed in
`k8s/README.md` section 6.3 after each deploy. If the rewrite does not work, the direct address `/api/public/cv/<handle>` is
the fallback, and `PUBLIC_CV_PATH` in `ui/src/publiccv/options.ts` decides which address the app shows.

## Known limits

- **No social previews.** Pasting the link into a chat shows no card: there are no Open Graph tags.
- **No PDF download.** The page is HTML only.
- **No directory or search** of CVs, by design. Handles are not listed anywhere.
- **No counters or analytics**: nobody sees how often a page is visited.
- **Preview opens in a new tab**, not inline, because the app's Content-Security-Policy would block the page's styles in a frame.
- **Live only**: there is no snapshot publishing yet.
- **Limits are per backend process.** Rate limits and the page cache live in memory, so each instance counts on its own.
- **The client address is best effort.** The per-IP limit uses the first `X-Forwarded-For` value; the per-handle limit is the backstop.
- **Edge behaviour** for `/cv`, `/cv/` and a trailing slash after a handle: from backend 0.17.0 every path under `/api/public/cv`
  answers the same uniform 404 as an unknown handle, for GET and HEAD, without sign-in. Backend 0.16.0 answered 401 for these
  (not permitted). The post-deploy checks in `k8s/README.md` section 6.3 must be re-run to confirm the 404 on the cluster.
