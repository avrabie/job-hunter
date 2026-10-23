# Report: ui-matches

## Built
- `ui/src/matches/`: `types.ts`, `matchesApi.ts` (`GET /api/matches?page&size=20`), `MatchesPage.tsx`, `MatchesPage.test.tsx`.
- `/matches` route in `App.tsx`; "Matches" button in `Shell.tsx`; small CSS rules in `index.css` (nested lists inside `.postings`).
- States: loading, empty (hint with link "Save your profile first" to `/profile`), error with "Try again", signed out (landing, no API call).
- Each item: title, company, city (canton), score, reasons (codes shown as returned), "Open original" only for http/https, `rel="noopener noreferrer"`.
- Previous/Next paging using the existing `page`/`size` parameters (page in the URL query).
- `ui/README.md` not changed.

## Checks (in ui/)
- `npx vitest run`: 153 passed (8 new, in 1 file)
- `npx tsc -b && npx vite build`: ok
- `npx oxlint --deny-warnings`: clean
- `npx prettier --check .`: clean

## Needs
- Reasons are shown as raw codes. A readable text per reason (or a documented code list) would be nicer:
  `reasons: [{code: "SKILL_MATCH", text: "Matches skill: kotlin"}]`, or the list of codes so the UI can map them.
- No detail link: `/postings/:id` exists, but the brief's shape lacks nothing here; `postingId` could link to it (UI-only, optional follow-up).
- The MatchItem shape was taken from the brief, not from `MatchItem` itself (not in the listed file); city/canton assumed nullable.
