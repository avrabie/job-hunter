# Group 5 report (public-cv-page, UI)

## Tasks finished
- 5.1 `npx vitest run src/publiccv`: 2 files, 48 tests, 0 failures (options.test.ts, publicCvApi.test.ts).
- 5.2 Full chain, all exit 0:
  - `npx vitest run`: 34 files, 435 tests, 0 failures (71 of them in src/publiccv).
  - `npx tsc -b`: exit 0.
  - `npx vite build`: exit 0 (build test "no inline script or style" passes).
  - `npx oxlint --deny-warnings`: exit 0.
  - `npx prettier --check .`: exit 0.

## Tasks not finished
- None.

## Files created or changed
- ui/src/publiccv/options.ts (new): settings types, `PUBLIC_CV_PATH = '/cv/'`, all-off `defaultSettings()`, `handleProblem`, `setSection`, `moveSection`.
- ui/src/publiccv/publicCvApi.ts (new): exports `getPublicCv`, `savePublicCv`, `removePublicCv`, `checkHandle`, `previewPublicCv`, `handleReasonMessage` and the types `PublicCvState`, `PublicCvSave`, `PublicCvResult`, `PublicCvErrorCode`, `HandleCheck`, `HandleReason`. Fixed messages per error code; bodies are never shown.
- ui/src/publiccv/publicCvApi.test.ts (new), ui/src/publiccv/options.test.ts (new).
- ui/src/publiccv/PublicCvPage.tsx (new): `PublicCvPage` (route wrapper, landing when signed out) and the form.
- ui/src/publiccv/PublicCvPage.test.tsx (new): 23 tests, one per scenario of the four `ui-cv-public` requirements plus the failure paths.
- ui/src/App.tsx: route `/publish`.
- ui/src/pages/Shell.tsx: link "Public CV".
- ui/src/index.css: `.public-cv-*` rules only.
- openspec/changes/public-cv-page/tasks.md: 5.1 and 5.2 ticked.
- handoff/pcv-g5.report.md (this file).

Not touched: ui/src/profile/ (the other agent's folder), lockfile, node_modules (git-ignored).

## Notes for the other agents and the lead
- Profile notice (6.1) can import `getPublicCv`, `PublicCvState` from `ui/src/publiccv/publicCvApi.ts` and `PUBLIC_CV_PATH` from `ui/src/publiccv/options.ts`.
- The preview opens `/api/cv/public/preview/<ticket>` with `window.open(path, '_blank', 'noopener')`; the path is checked against a fixed pattern first, and the page never reads or stores the HTML.
- Sections: the ticked ones are listed first in the order chosen (move up/down only among them); unticked ones follow in the default order.
- Clicking Remove shows an inline confirmation (no `window.confirm`).
- The "can change from" date is compared with the clock read when the page opened.
- Handle availability is skipped when the handle equals the saved one (the owner's own handle).

## Surprises
- Fake timers and `findBy*` do not mix (testing-library's `waitFor` hangs); tests use synchronous queries after `advanceTimersByTimeAsync`.
- A 204 response cannot take an empty-string body in the test stub; the stub takes `null`.
- The backend is not in this worktree: all server behaviour is taken from the Contract in `design.md`. Field-level 400 messages are shown from the fixed handle text, since the Contract only names the field.
