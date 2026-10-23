# ui-cleanup report

Status: FINISHED. All verify commands pass. The lead approved the one-file test change after my first report.

## Tasks finished

- (1) Dead `/api/cv` code removed from `ui/src/cv/cvApi.ts`: `loadCv`, `uploadCv`, `deleteCv`, `downloadCv`, `CvInfo`, `CvResult`, `UploadResult`, `DownloadResult`, `DeleteResult`, `UPLOAD_MESSAGES`, `ACCEPTED_TYPES`. Kept `extractProfile`, `applyDraft`, `withDefaults`, `EXTRACT_MESSAGES`, `DraftResult`, `guarded`, `PATH`. Grep across `ui/src` confirmed no importer of the removed names. The only importers of `cvApi` are `ProfilePage.tsx` (`applyDraft`) and `FileCabinet.tsx` (`extractProfile`).
- (1) Matching tests removed from `ui/src/cv/cvApi.test.ts`. `extractProfile` and `applyDraft` tests kept unchanged.
- (2) `listFiles` in `ui/src/files/filesApi.ts`: 404 special case removed, so 404 maps to `failed` with the usual `messageForStatus(404)` text, "The backend has no endpoints yet.".
- (2) `ui/src/files/filesApi.test.ts`: test "treats 404 as a failure" expects `{ kind: 'failed', message: 'The backend has no endpoints yet.' }`.
- Follow-up (lead-approved): `ui/src/profile/ProfilePage.test.tsx` fetch stub. `/api/files` now answers 200 with `[]`; any other `/api/files...` path still answers 404. Nothing else in the file changed.

## Verify output

- Setup: `ui/` had no `node_modules`. Installed with `npx --yes pnpm@12.14.0 install --frozen-lockfile` (the version from `packageManager`). `pnpm@9` fails with ERR_PNPM_BROKEN_LOCKFILE on `pnpm-lock.yaml`. Lockfile unchanged.
- `npx vitest run`: 24 files passed, 222 of 222 tests passed.
- `npx tsc -b`: exit 0.
- `npx vite build`: exit 0.
- `npx oxlint --deny-warnings`: exit 0.
- `npx prettier --check .`: all files pass.

## Tasks not finished and why

- None. This cleanup has no task id in tasks.md, so nothing was ticked.

## Files created or changed

- `ui/src/cv/cvApi.ts`
- `ui/src/cv/cvApi.test.ts`
- `ui/src/files/filesApi.ts`
- `ui/src/files/filesApi.test.ts`
- `ui/src/profile/ProfilePage.test.tsx`
- `handoff/ui-cleanup.report.md` (this file)
- Gitignored, not in git status: `ui/node_modules/`, `ui/dist/`.

Nothing committed. No backend, `k8s/` or other files touched. Worktree git status shows only the five `ui/src` files above plus this report.

## Surprising / decisions for the lead

- `pnpm@9` cannot read `ui/pnpm-lock.yaml`; `pnpm@12.14.0` works. Unrelated to this change, but CI may hit the same error.
