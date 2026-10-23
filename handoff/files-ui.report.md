# Report: files-ui (file-cabinet tasks 7.1 to 7.3)

## Tasks finished
- 7.1 `ui/src/files/filesApi.ts`: `npx vitest run src/files` gave 27 passed (at that time).
- 7.2 `ui/src/files/FileCabinet.tsx` with component tests: 93 passed across `src/files` and `src/profile` (at that time).
- 7.3 fill button moved into the cabinet's master area, `CvSection` removed. Full chain on `ui/`:
  - `npx vitest run`: 24 files, 224 tests passed.
  - `npx tsc -b`: ok. `npx vite build`: ok.
  - `npx oxlint --deny-warnings`: clean. `npx prettier --check .`: clean.
- Ticked 7.1, 7.2, 7.3 in `openspec/changes/file-cabinet/tasks.md`. Nothing committed.

## Tasks not finished
None.

## Files created
- `ui/src/files/filesApi.ts` (list, delete, setMaster, clearMaster, requestDownload, uploadFile over XHR)
- `ui/src/files/filesApi.test.ts`
- `ui/src/files/fakeXhr.ts` (test-only stand-in for XMLHttpRequest, imported by tests only)
- `ui/src/files/FileCabinet.tsx` (props `formHasContent`, `onDraft`)
- `ui/src/files/FileCabinet.test.tsx` (cabinet scenarios and the ui-cv fill scenarios)
- `ui/src/files/format.ts`, `ui/src/files/format.test.ts` (KB/MB/GB)
- `handoff/files-ui.report.md`

## Files changed
- `ui/src/profile/ProfilePage.tsx`: `CvSection` replaced by `FileCabinet` with the same draft wiring (`applyDraft`).
- `ui/src/profile/ProfilePage.test.tsx`: the stub answers `/api/files` with 404 (as it did for `/api/cv`); comment updated.
- `ui/src/cv/cvApi.ts`: 409 message in `EXTRACT_MESSAGES`.
- `ui/src/cv/cvApi.test.ts`: 409 case added.
- `openspec/changes/file-cabinet/tasks.md`: three ticks.

## Files removed
- `ui/src/cv/CvSection.tsx`
- `ui/src/cv/CvSection.test.tsx`

## Local only (not in git)
- `ui/node_modules` is a symlink to the main checkout's `ui/node_modules`, because the worktree had none and pnpm is not installed. Delete it if you do not want it.

## Decisions and assumptions to confirm
1. The list answer of `GET /api/files` is assumed to be a JSON array of file details. The Contract only gives the details object.
2. A 404 from `GET /api/files` is treated as an empty cabinet, as `loadCv` did with `/api/cv`. This was needed because `ProfilePage.test.tsx` asserts `queryByRole('alert')` is absent. Side effect: a missing endpoint looks like an empty cabinet.
3. After delete and master changes the list is updated from the server answer (no second GET). The spec says "refreshed"; I read it as "updated from the answer".
4. Client-side fixed messages not in the Contract: master 400 "Only a CV file can be the master CV." and 404 "That file no longer exists."; upload 413 for OTHER "The file is larger than 10 GB.".
5. The download ticket answer is validated: the path must start with `/api/files/` and pass `isApiPath`, otherwise no navigation.

## Left for the lead (outside my files)
- `ui/src/cv/cvApi.ts` still exports `loadCv`, `uploadCv`, `deleteCv`, `downloadCv` with their tests. They call `/api/cv`, which backend task 6.2 removes. They are now dead code. I kept them because the task said to keep the `applyDraft` tests and the file was not in my list. Suggest removing them with 6.2 or in a cleanup.
- `ui/src/index.css`: `.cv-section` is now unused. The cabinet uses class `file-cabinet` and has no own CSS rule.
- The privacy test from the old `CvSection.test.tsx` (file name not in storage or console) was not carried over. The cabinet writes no logs.
- `CvSection.test.tsx` was red between 7.2 and 7.3 (it mounted the page and looked for the old control). It is removed in 7.3, so the suite is green at the end.
