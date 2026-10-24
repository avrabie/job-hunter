# pcv-g6b report (profile notice on the shared public CV client)

Tasks: none in tasks.md; this was the requested refactor of the group 6 notice (no checkbox ticked).

Verify (ui/, real exit codes, run unpiped):
- `npx vitest run`: exit 0, 35 files, 444 tests passed (profile dir: 8 files, 156 tests).
- `npx tsc -b`: exit 0.
- `npx vite build`: exit 0.
- `npx oxlint --deny-warnings`: exit 0.
- `npx prettier --check .`: exit 0.

What changed:
- `loadPublicCvState(auth)` now calls `getPublicCv` from `ui/src/publiccv/publicCvApi.ts`. Result `kind !== 'ok'` (unauthorized or failed) gives `unknown`. A `try/catch` keeps it from throwing. The shared client does not check the body, so a small `noticeOf` maps `configured`/`published`/`handle` with the same rules as before.
- `publicCvAddress` builds the address from `PUBLIC_CV_PATH` (`/cv/`) and stays in `publicCvNotice.ts`. Nothing moved into `ui/src/publiccv/`.
- Duplicate fetch code and the duplicate wire types are gone. The local `PublicCvState` view type keeps its name and shape; the shared type is imported as `PublicCvPageState` to avoid a clash.

Files changed:
- ui/src/profile/publicCvNotice.ts

Not changed: `publicCvNotice.test.ts` (all scenarios still pass as written: live, not-live none/paused, 500, bad body, connection failure, no token, address), `ProfilePage.tsx`, `ProfileForm.tsx`.

Notes:
- `ui/node_modules` was missing; installed with `npx --yes pnpm@12.14.0 install --frozen-lockfile`. No lockfile change, nothing committed.
- Vitest prints a jsdom "Not implemented: navigation" line that predates this change; it does not fail the run.
