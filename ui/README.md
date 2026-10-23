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

The signed-in page calls `/api/` once and shows "The backend has no endpoints yet." until `backend` has endpoints.

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

`/admin` shows the crawl schedule (enabled, cron, time zone, next run, last change) from `GET /api/admin/crawl-schedule` and saves changes with `POST` to the same path, using the signed-in user's own token. The "Admin" button in the shell appears only when the access token's `roles` claim contains `ADMIN`; the token's signature is not checked in the browser, so this decides what to show and nothing else. The backend enforces the role, and the page shows "This page is for administrators." when the API answers 403. The result of the last crawl run is not shown, because the API does not offer it.

## Profile page check (local)

Checked by hand in a browser on 2026-10-21 against the real local stack (`auth`, `backend`, `gateway`, `pnpm dev`):

- Opening `/profile` signed out starts the sign-in and returns to `/profile`; a reload on `/profile` returns to it too.
- A lowercase canton is rejected by the API and the message appears at the canton field; the other edits stay.
- Save shows "Saved (version 1)"; after a reload the values are back; browser storage holds only the `jh.signedIn` hint.
- A second tab saved version 2; saving from the first tab (still at version 1) showed the conflict notice with the edits kept and Save paused; "Reload" loaded the second tab's values and re-enabled Save.
- The console showed only Vite's own dev messages.
