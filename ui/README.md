# ui

The web UI: React and TypeScript on Vite. It signs users in at our own `auth` service (authorization code + PKCE,
client `spa`) and calls `/api` with the user's token. It is a fourth independent build next to `backend/`, `auth/` and
`gateway/`. Rationale and requirements: `openspec/changes/bootstrap-ui` (or the archived change and the `ui-*` specs).

## Requirements

- Node 22 or newer (developed on Node 25).
- pnpm 12.14.0, the version pinned in `package.json`. Node 25 does not bundle Corepack, so install it with
  `npm i -g pnpm@12.14.0`. With an older Node, `corepack enable` picks the pinned version by itself.

## Commands

Run these in `ui/`.

| Command          | What it does                                                            |
| ---------------- | ----------------------------------------------------------------------- |
| `pnpm install`   | Installs the locked dependencies (`--frozen-lockfile` in CI and Docker) |
| `pnpm dev`       | Dev server on http://localhost:5173                                     |
| `pnpm test`      | Unit and component tests (Vitest, jsdom)                                |
| `pnpm typecheck` | TypeScript, no emit                                                     |
| `pnpm lint`      | oxlint (warnings are errors) and a Prettier check                       |
| `pnpm format`    | Rewrites files with Prettier                                            |
| `pnpm build`     | Type check and production build into `dist/`                            |

`pnpm lint && pnpm typecheck && pnpm test && pnpm build` is what must pass before a commit.

## Local development

The browser must see one origin, as behind the edge in production. `pnpm dev` serves the UI on
http://localhost:5173 and proxies `/api` and `/auth` to the local gateway on port 8080 (`GATEWAY_URL` changes the
target). The UI takes its sign-in addresses from the page's origin, so the issuer that `auth` advertises has to be
`http://localhost:5173/auth` while you work on the UI. The defaults of the other services use the gateway's address
(`http://localhost:8080/auth`), so start `auth` and `backend` with `ISSUER_URI` set. From the repository root, each in
its own terminal (see `gateway/README.md` for the services themselves):

```bash
(cd auth && ISSUER_URI=http://localhost:5173/auth ./gradlew bootRun --args='--spring.profiles.active=dev')
(cd backend && ISSUER_URI=http://localhost:5173/auth ./gradlew bootRun --args='--spring.profiles.active=dev')
(cd gateway && ./gradlew bootRun)
(cd ui && pnpm dev)
```

Open http://localhost:5173, choose Register, create an account and sign in. `backend` reads the issuer lazily through
the dev proxy, so keep `pnpm dev` running while you call `/api`. The `dev` profile of `auth` already allows the
redirect addresses `http://localhost:5173/callback` and `http://localhost:5173/`.

After signing in, the navigation bar links the postings, matches, job tracking, CV and profile pages; the backend endpoints they call are listed in `backend/README.md`.

## Production check

Checked by hand in a browser at `https://s4v3.net` on 2026-10-21, with the console open:

- Register, then sign in at `auth`: works.
- The shell shows the signed-in identity and the "no endpoints yet" message; reload keeps the session; sign-out
  clears it and a reload shows the landing page.
- The only console entry is the browser's own log line for the expected `404` of `GET /api/`; no other errors and no
  Content-Security-Policy violations.

## Postings page

`/postings` lists the postings the backend crawled (newest first, 20 per page) with a title that opens `/postings/:id`
(the full description as plain text), the company, city, language, the first-seen date and one **Open original** link
per source. Search (title or company), canton and source filters and the page live in the URL query
(`?q=&canton=&source=&page=`), so a view can be bookmarked. Links are rendered only for `http` and `https` addresses and
open in a new tab with `rel="noopener noreferrer"`. A signed-out visitor sees the landing page and returns here after
signing in. The "next" button is enabled while a page is full, so the last page can be empty when the total is a multiple of 20.

`/admin` shows the crawl schedule (enabled, cron, time zone, next run, last change) from `GET /api/admin/crawl-schedule` and saves changes with `POST` to the same path, using the signed-in user's own token. The "Admin" link in the navigation bar appears only when the access token's `roles` claim contains `ADMIN`; the token's signature is not checked in the browser, so this decides what to show and nothing else. The backend enforces the role, and the page shows "This page is for administrators." when the API answers 403. The result of the last crawl run is not shown, because the API does not offer it.

## Publications (profile page)

The profile page has a **Publications** section after Education with the same add, remove and move controls: Title (required),
Venue, Year, Authors and Link. The browser checks the cheap rules when a field is left (title not blank, year 1900 to next
year, link an absolute `http` or `https` address); the server stays the authority and its message is shown at the field if
the profile is saved anyway.

## Technologies and Open Source Projects (profile page)

Each position has a **Technologies** field made of tags: type a name and press Enter or a comma, Backspace in the empty input
removes the last tag, each tag has a remove button, and text still in the input when the field is left is added. Tags are
trimmed and a tag that differs from an existing one only in case is ignored (the first spelling stays). Suggestions come from
the skills and from tags already used in positions.

The **Open Source Projects** section replaces the old Projects section (work belongs in the positions). Add a code account
(provider, GitHub for now, and a username; checked in the browser when the field is left), press **Load repositories** to
see the account's public repositories as a checklist, and tick the ones to show. Ticked repositories are copied into the
**Picked projects** list, where each has a **Note**, move up/down and remove controls; repositories that disappear from the
account stay until removed. Loading saves nothing: the page says so and the picks are saved with **Save**. Fixed messages
cover an unknown account, rate limiting and an unreachable provider. There is no "Fill from CV" on this section.

Each saved project has an **Analyze** button (disabled until the project is saved). It calls
`POST /api/profile/open-source-projects/analyze`, shows the summary, the architecture and the technologies with their evidence, and
adds the technologies to **Skills** as unsaved changes: an existing skill of the same name (any letter case) gets the repository as a
source and keeps its level, any other technology becomes a skill from repository analysis with no level. Skills from analysis show
their source links and evidence, and each skill and source can be removed. Analysing again replaces the repository's earlier sources
(after a confirmation), and removing a project or its analysis takes its sources off the skills. Fixed messages cover each error code.

## Contact and CV export

The profile page starts with a **Contact** section (full name, headline, email, phone, website, LinkedIn; checked in the browser when a
field is left, the server stays the authority). The full name is needed to export. **Export CV** (navigation bar under CV, and the profile page) opens `/export`:
choose format (Markdown, HTML, PDF), template (Classic, Modern, Compact), language (English, Deutsch), the sections (tick, move up/down)
and the options for open source projects, analysis summaries and work authorisation. The preview is the HTML version in a sandboxed
`iframe` (`srcdoc`, no permissions), refreshed shortly after a change; **Download** saves the file the server names. If the server answers
`name_required` the page links to the Contact section. Nothing of the profile or the file is kept in browser storage.

## Public CV page

`/publish` (linked as **Public page** under CV in the navigation bar; signed-out visitors see the sign-in page first) is the owner's control for the
public CV page of the `cv-public` spec. Code: `ui/src/publiccv/` (`PublicCvPage.tsx`, `publicCvApi.ts`, `options.ts`).

- **Handle.** The field lower-cases what is typed. After a short pause (400 ms) the server checks it (`GET /api/cv/public/handle`) and
  the text under the field says whether it is free, invalid, reserved, taken or held. The field shows `Your page will be at …/cv/<handle>`.
  When the handle is locked by the 30-day rule the page says the date it can be changed.
- **Layout and ticks.** Template (Classic, Modern, Compact) and language (English, Deutsch). Sections: the ticked ones come first, in the
  order set with the move up and move down buttons; the others follow. Ticks: headline, analysis summaries, work authorisation, location,
  email, phone, website, LinkedIn, code-account links, and **Allow search engines** (off by default). The text on the page says that the
  full name is always shown and preferences never are.
- **Status and the live banner.** Published: "Your CV is live at <address>. Every change you save in your profile shows there at once,
  in the fields ticked above.", with **Copy link**. Not published: "Your CV is not public." An administrator's takedown shows
  "An administrator disabled this page. Publishing is not possible." and disables **Publish**.
- **Buttons.** **Publish** (when not live), **Pause** (saves with `published: false`; the address answers 404 and the settings are
  kept), **Save**, **Preview** and **Remove** (after an inline confirmation; the handle is kept for 90 days).
- **Preview** asks the backend for a one-use ticket and opens `/api/cv/public/preview/<ticket>` in a new tab (`window.open(…, '_blank',
'noopener')`). It is not shown inline: the app's Content-Security-Policy (`style-src 'self'`) would block the page's styles in a frame.
  The page never reads or stores the HTML.
- **Profile page notice.** `ui/src/profile/publicCvNotice.ts` reads the state with the shared client. When the page is live the profile
  page says "Your public CV is live at <address>" (`ProfileForm.tsx`). A failed read shows nothing.
- **Address constant.** `PUBLIC_CV_PATH = '/cv/'` in `ui/src/publiccv/options.ts` is the path shown to users; the page and the profile
  notice both build the address from it and from `window.location.origin`.
- Nothing of the page's content or the handle is kept in browser storage.

## File cabinet (profile page)

The top of `/profile` has **Your files**: every file with its kind (CV or Other), size (KB, MB, GB), date and a **Master CV**
label. Upload takes a file and a kind: **CV** accepts `.pdf`, `.md` and `.txt` up to 5 MB; **Other file** accepts anything up to
10 GB on paper; on s4v3.net an upload has to finish within about a minute (the proxy's read timeout). Uploads use `XMLHttpRequest` (not `fetch`) so there is a progress bar and a Cancel button, and the file is never read
into page memory. **Download** asks for a one-time ticket with the user's token and then points the browser at the returned path,
so a large file streams straight to disk. **Delete** asks first. A CV file has **Use as master CV** (the master has **Remove
master**); nothing is chosen automatically except for a CV uploaded before the cabinet existed, which was moved in as master.

**Fill the profile from my CV** works on the master CV: it is disabled with a hint until one is chosen. It asks the backend to
read the CV and puts the result in the form **as unsaved changes**, with a notice; only the page's own Save button sends
anything to `PUT /api/profile`. When the form already has content a confirmation comes first, and the user's preferences
(roles, workload, salary...) are always kept because a CV does not contain them. Experience, Education and Publications each have a **Fill from CV** button that reads the master CV for that section only and
replaces just that section's entries in the form (after a confirmation when it already has entries), as unsaved changes. The
buttons are disabled with a hint until a master CV is chosen. Errors show fixed texts per status (no master,
too large, no text in the CV, too many extractions, model unavailable, no room on the server).

## Profile page check (local)

Checked by hand in a browser on 2026-10-21 against the real local stack (`auth`, `backend`, `gateway`, `pnpm dev`):

- Opening `/profile` signed out starts the sign-in and returns to `/profile`; a reload on `/profile` returns to it too.
- A lowercase canton is rejected by the API and the message appears at the canton field; the other edits stay.
- Save shows "Saved (version 1)"; after a reload the values are back; browser storage holds only the `jh.signedIn` hint.
- A second tab saved version 2; saving from the first tab (still at version 1) showed the conflict notice with the edits kept and Save paused; "Reload" loaded the second tab's values and re-enabled Save.
- The console showed only Vite's own dev messages.
