# Report: github-open-source-projects, UI tasks 4.1 to 4.3

## Tasks finished

- **4.1** types, rules, fixture and two test files. Verify `npx vitest run src/profile/rules.test.ts src/profile/profile.test.ts`:
  2 files, 33 tests passed, 0 failed. The drift check (key sets of the fixture and `emptyProfile()`) passes with the copied fixture.
- **4.2** `repositoriesApi.ts` and its test file. Verify `npx vitest run src/profile/repositoriesApi.test.ts`:
  1 file, 10 tests passed (success, empty list, the four listing codes, 429 without a code, generic 500, network failure, 401).
- **4.3** `OpenSourceSection.tsx`, `ProfileForm.tsx`, `ProfilePage.tsx`, `cv/cvApi.ts`, `ProfilePage.test.tsx`.
  Full chain `npx vitest run && npx tsc -b && npx vite build && npx oxlint --deny-warnings && npx prettier --check .` exits 0:
  - vitest: 26 files, 269 tests passed, 0 failed (ProfilePage.test.tsx: 38 passed).
  - tsc -b: no errors.
  - vite build: built, 133 modules.
  - oxlint: no findings.
  - prettier: all files formatted.

Ticked in `openspec/changes/github-open-source-projects/tasks.md`: 4.1, 4.2, 4.3. No task left open.

## Files changed (not committed)

- `openspec/changes/github-open-source-projects/tasks.md` (ticks only)
- `ui/src/profile/types.ts`, `rules.ts`, `rules.test.ts`, `profile.test.ts`
- `ui/src/profile/fixtures/full-profile.json` (copied from `backend/src/test/resources/profile/full-profile.json`, then prettier-formatted; same values)
- `ui/src/profile/repositoriesApi.ts` (new), `repositoriesApi.test.ts` (new)
- `ui/src/profile/OpenSourceSection.tsx` (new)
- `ui/src/profile/ProfileForm.tsx`, `ProfilePage.tsx`, `ProfilePage.test.tsx`
- `ui/src/cv/cvApi.ts` (`ProfileSectionName` without `projects`)

## Decisions and surprises

- **Order matters for tsc.** After 4.1 alone, `tsc -b` fails in `ProfileForm.tsx` and `ProfilePage.test.tsx` (they still use `projects`).
  The 4.1 verify (vitest on two files) passes anyway. 4.3 removes those uses, and tsc is clean after 4.3.
- **Repository listing is injected.** `ProfilePage` passes `onLoadRepositories={(p, u) => listRepositories(auth, p, u)}` through
  `ProfileForm` to `OpenSourceSection`. The section itself holds no auth.
- **Error mapping.** `repositoriesApi.ts` reads the `error` code from the body (`invalid_username`, `account_not_found`,
  `provider_rate_limited`, `provider_unavailable`). A 429 without a code maps to the rate-limit text. Anything else uses `messageForStatus`.
- **Account rule.** The section uses a `Repeater` for accounts with `max={CODE_PROVIDERS.length}`, which is one per provider today.
  The username is checked in the browser through `isValidUsername` (exported from `rules.ts`), the same rule as the backend.
  The server message for a bad username is shown through the field's error path `codeAccounts[i].username`.
- **Picks.** Ticking copies the listing facts with `note: null`. Unticking removes by `provider` + `fullName`.
  A picked repository missing from the list stays. After a tick, the section shows "Nothing is saved until you press Save."
- **Unauthorized on listing** shows nothing: the token is discarded and `ProfilePage` switches to the sign-in page, as with save.
- **No CSS added.** The spec allows CSS for the new section, but the existing classes (`section-heading`, `hint`, `notice`,
  `actions compact`, `inline`) covered the layout, so `index.css` is unchanged.
- **Removed from the page:** the Projects section, `PROJECT_TYPE_LABELS`, the `projects` fill state and title, and the
  `projects` suggestions for technologies.
- **Local setup, not committed.** The worktree had no `node_modules`, so `ui/node_modules` is a symlink to the main checkout's
  `ui/node_modules` (gitignored). `npx` was used throughout, never pnpm.
- **Console noise.** The full vitest run prints "Not implemented: navigation to another Document" once. It did not fail the run,
  and I did not trace it to a test.

## Not done here

- Nothing in the backend or docs (groups 1 to 3 and 5 are untouched).
- No browser check (the project rule is to test on the deployed site).
