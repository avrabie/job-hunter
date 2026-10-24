# Report: an-ui (tasks 5.1 to 5.3, UI)

Change: `analyze-open-source-projects`. Worktree `worktree-agent-a3d468ffa33547415`. Nothing committed.

## Tasks finished

- **5.1** types, pure skill merge, fixture. Verify `npx vitest run src/profile`: 7 files, 111 tests passed (at that point; the full run is below).
- **5.2** `analyzeProject` in new `analysisApi.ts`. Verify: `analysisApi.test.ts`, 10 tests (success, 7 codes, 429 without code, other status, network failure, 401) passed.
- **5.3** Analyze button, loading, confirm, analysis display, fixed messages, Skills origin and sources. Verify: `ProfilePage.test.tsx` has 12 new tests (`Analyze a picked project` 5, `Traceable skills from analysis` 7) passed.

Full check chain (in `ui/`), all green:
- `npx vitest run`: 28 files, 305 tests passed (baseline was 26 files, 269).
- `npx tsc -b`: no errors.
- `npx vite build`: built.
- `npx oxlint --deny-warnings`: exit 0.
- `npx prettier --check .`: all files pass.

Tasks not ticked: I did not edit `openspec/.../tasks.md` because the instruction was to edit only `ui/` files. The lead should tick 5.1, 5.2 and 5.3 after reviewing this report.

## Files

Changed:
- `ui/src/profile/types.ts`
- `ui/src/profile/fixtures/full-profile.json`
- `ui/src/profile/profile.test.ts`
- `ui/src/profile/OpenSourceSection.tsx`
- `ui/src/profile/ProfileForm.tsx`
- `ui/src/profile/ProfilePage.tsx`
- `ui/src/profile/ProfilePage.test.tsx`

Created:
- `ui/src/profile/skills.ts`
- `ui/src/profile/skills.test.ts`
- `ui/src/profile/analysisApi.ts`
- `ui/src/profile/analysisApi.test.ts`
- `handoff/an-ui.report.md` (this file)

## Fixture and drift

- The Contract's Skill JSON is the third entry in `skills`, after Kotlin and PostgreSQL, which are unchanged.
- The first project has the Contract analysis. Its summary is "Invented sample project used only in tests." and its architecture is "One Gradle module with a Spring Boot application.".
- The existing Kotlin and PostgreSQL entries have no `origin` or `sources`. I left them as they are so the fixture still matches the backend sample. The UI reads a missing `sources` as `[]` (`sourcesOf` in `skills.ts`) and `toRequestProfile` sends `origin` and `sources` as they are, so an old skill goes out without them and the server defaults them.
- At merge, the drift test may fail if the backend fixture differs in these two skills or in the project key set. I have not compared against the backend file, since it is outside `ui/`.

## Decisions and deviations

- `toRequestProfile` now sends `analysis` on each project and `origin` and `sources` on each skill. The "goes to the wire unchanged" test covers this.
- Added a "Remove analysis" button per project. The spec says removing "its analysis" removes its sources, so a control was needed. `ProfileForm` removes sources whenever a project is removed or loses its analysis (`skillsAfterProjectChange`).
- The client maps seven error codes to fixed messages. `invalid_request` (400) falls through to the generic status message. The spec lists seven codes and does not list this one.
- Loading and failure state in `OpenSourceSection` is keyed by the project's list position, not its key. If the list changes while an analysis is running, a message can show on a neighbouring row. Keying by `provider:fullName` would fix it; I did not change it.
- Analyze is disabled until the project is in the last loaded or saved profile. The hint reads "Save this project first to analyze it."
- Spelling: the UI uses "Analyze" everywhere, as the spec does.
- `ui/node_modules` is a symlink to the main checkout's `ui/node_modules`, because the worktree had none. It is gitignored. `ui/dist/` from `vite build` is also gitignored.

## Surprising

- The `ProfilePage.test.tsx` and `profile.test.ts` edits add `analysis: null` to literal projects and expected objects. These are required by the new type, not weakened assertions.
- Vitest does not type-check, so `npx tsc -b` was run separately and caught three real errors that the tests had missed. All are fixed.

## Decisions I need from the lead

1. Tick 5.1, 5.2 and 5.3 in `tasks.md`.
2. Confirm the fixture choice above: existing skills unchanged, with reads tolerant of a missing `sources`.
