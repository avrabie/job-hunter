# Brief: ui-matches (UI agent)

**Goal:** a signed-in user can see their prefilter matches in the UI at `/matches`: title, company, city, score, the reasons,
and an "Open original" link. Reuse the existing patterns; do not invent a new style.

**Read only:**
- `ui/src/postings/PostingsPage.tsx`, `postingsApi.ts`, `OriginalLinks.tsx`, `links.ts`, `PostingsPage.test.tsx` (the pattern to copy)
- `ui/src/App.tsx`, `ui/src/pages/Shell.tsx`, `ui/src/test/fakeAuth.ts`
- `openspec/specs/ui-postings/spec.md` (for the style of requirements)
- API (already deployed): `GET /api/matches` returns `[{postingId, title, company, city, canton, url, score, reasons}]`
  for the signed-in user only, best score first. Check the exact shape in
  `backend/src/main/kotlin/net/s4v3/backend/matching/internal/MatchesController.kt` (read that one file).

**May edit:** `ui/src/matches/` (new), `ui/src/App.tsx`, `ui/src/pages/Shell.tsx`, `ui/src/index.css`, `ui/README.md`.
Nothing else. Do not touch `backend/`, `auth/`, `k8s/`, `openspec/`.

**Done when:** `/matches` works with loading, empty ("save your profile first" hint), error-with-retry and signed-out states;
a Matches button in the shell; links only for http/https with `rel="noopener noreferrer"`; component and API tests;
in `ui/` these pass: `npx vitest run`, `npx tsc -b && npx vite build`, `npx oxlint --deny-warnings`, `npx prettier --check .`.

**Report (`handoff/ui-matches.report.md`):** what you built, test counts, and a `Needs` section for anything the backend should
provide (for example paging, a posting id for the detail link, or the match score on `GET /api/postings`), with the exact
shape you want. If the API is enough, write "Needs: none".
