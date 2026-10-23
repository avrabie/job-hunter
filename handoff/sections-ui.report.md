# Report: sections-ui (cv-sections tasks 2.1 and 2.2)

## Tasks finished
- 2.1 `ui/src/cv/cvApi.ts`: `extractSection(auth, section)` and `ProfileSectionName`. `extractProfile` and the new function share `runExtract`.
  - `npx vitest run src/cv`: 18 passed (cvApi tests: query string, 400, 404, 409, 413, 422, 429, 502, 503).
- 2.2 Page knows the master, three "Fill from CV" buttons, confirmation, unsaved notice.
  - `npx vitest run` (whole `ui/`): 24 files, 238 tests passed (was 222 before this change plus the new cases).
  - `npx tsc -b`: exit 0. `npx vite build`: exit 0. `npx oxlint --deny-warnings`: exit 0. `npx prettier --check .`: clean.
- Ticked 2.1 and 2.2 in `openspec/changes/cv-sections/tasks.md`. Tasks 1.x, 3.1 untouched.

## Tasks not finished
- None.

## Files changed
- `openspec/changes/cv-sections/tasks.md` (two ticks)
- `ui/src/cv/cvApi.ts`, `ui/src/cv/cvApi.test.ts`
- `ui/src/files/filesApi.ts` (`loadMaster`, `MasterResult`), `ui/src/files/filesApi.test.ts` (two cases)
- `ui/src/files/FileCabinet.tsx` (optional prop `onMasterChange`)
- `ui/src/profile/ProfileForm.tsx` (props `canFillFromCv`, `onFillSection`, `fillState`; `FillState`; section headings)
- `ui/src/profile/ProfilePage.tsx` (master load, fill state, confirmation, notice)
- `ui/src/profile/ProfilePage.test.tsx` (new describe block with its own stub; `stubApi` unchanged)

## Behaviour
- `ProfilePage` calls `GET /api/files/master` once on mount. 200 means a master exists, 404 means none. Other failures leave `hasMaster` false, so the buttons stay disabled with the hint and no message.
- `FileCabinet` calls `onMasterChange(true)` after a successful "Use as master CV", `onMasterChange(false)` after "Remove master", and `onMasterChange(false)` when the master file is deleted.
- A section fill replaces only `draft[section]`. It asks `window.confirm` when that section has entries; declining sends no request. Success shows a notice that nothing is saved until Save. It sends no PUT.
- Errors show in the section under its heading, using `EXTRACT_MESSAGES` (same texts as the whole-profile button).

## Decisions and assumptions to confirm
1. 409 text: the task text says "Choose a master CV first.", but note (a) said to reuse the existing fixed message. I used the existing one: "Choose a master CV in your files first.". The Contract text is the server body, which the UI never shows. Tell me if the UI text should change.
2. 400 text: "That section cannot be read." (new, from the task text). Only reachable with an unknown section, which the UI never sends.
3. `GET /api/files/master` is assumed to answer 200 with a file when a master exists and 404 when none. Not checked against the backend (other worktree).
4. Hint text when disabled: "Choose a master CV in your files to use this." (shown under each of the three headings).
5. `onMasterChange` is optional, so `FileCabinet.test.tsx` is unchanged.
6. The `ProfilePage.test.tsx` stub in `stubApi` is unchanged: `/api/files/master` already gets 404 there, and `/api/files` already gets `[]`.

## Surprising
- `ui/node_modules` was missing in this worktree. I ran `npx --yes pnpm@12.14.0 install --frozen-lockfile` inside `ui/`. It is gitignored and not committed.
- Nothing committed. No backend, `k8s/` or other files touched.
