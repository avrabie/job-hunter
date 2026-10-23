# exp-ui report (experience-projects, tasks 3.1 to 3.4)

## Tasks finished

- 3.1 Types, tidying, rules, fixtures. `npx vitest run src/profile`: 77 passed (at that point). Typecheck clean.
- 3.2 TagInput. `src/profile/TagInput.test.tsx`: 5 passed.
- 3.3 Technologies on positions, Projects section, component tests. `src/profile`: 69 passed.
- 3.4 `projects` section name, Fill from CV on Projects, four disabled buttons test.
- Full chain, from `ui/`: `npx vitest run` 25 files, 252 passed. `npx tsc -b` ok. `npx vite build` ok.
  `npx oxlint --deny-warnings` exit 0. `npx prettier --check .` ok.

Tasks not finished: none. 4.x and the backend (1.x, 2.x) were out of scope.

## Files changed

- ui/src/profile/types.ts
- ui/src/profile/rules.ts
- ui/src/profile/fixtures/full-profile.json
- ui/src/profile/profile.test.ts
- ui/src/profile/rules.test.ts
- ui/src/profile/TagInput.tsx (new)
- ui/src/profile/TagInput.test.tsx (new)
- ui/src/profile/ProfileForm.tsx
- ui/src/profile/ProfilePage.tsx
- ui/src/profile/ProfilePage.test.tsx
- ui/src/profile/fields.tsx
- ui/src/cv/cvApi.ts
- ui/src/index.css
- backend/src/test/resources/profile/full-profile.json (fixture only, see deviation 1)
- openspec/changes/experience-projects/tasks.md (ticked 3.1 to 3.4)
- ui/node_modules installed with pnpm (gitignored, not committed). Nothing committed.

## Deviations and doubts

1. Backend fixture: the Contract sample was added to `backend/src/test/resources/profile/full-profile.json`
   as note (a) asks; no other backend file touched. The file is not byte-identical to the UI copy (its
   formatting differs), but the parsed JSON is equal (checked with Python). Task 1.3 also edits this file,
   so expect a merge conflict; the same content should win.
2. Second position in both fixtures got `"technologies": []`. The Contract only names the first one. Without
   it the UI crashed (the spec says every entry has the list), and the server always sends the list.
3. `fields.tsx`, not named in the task: `Select` got an optional `labels` prop (to show "Open source" for
   OPEN_SOURCE), and `Repeater` got an optional `max` prop (Add disabled at 5 links).
4. `TagInput` got an optional `error` prop, not in the task's prop list. Without it, server messages on a
   tag (for example `experience[0].technologies[1]`) would be invisible. It shows the first message for the
   list path or any entry of it. Limit: the error does not move focus after a failed save, because the
   focus logic matches `data-path`, which is the list path, not the per-tag key.
5. `index.css`: one new block of classes (`.tag-list`, chips). No existing chip style existed.
6. `ui/src/profile/types.ts` also exports `PROJECT_TYPES`, `blankProjectLink` and `tidyTags`. `blankProject()`
   defaults to type ASSIGNMENT, so the Type select has no "not set" option.
7. Link checks in `rules.ts` read `profile.projects` as typed, not the request copy, so that row indexes match
   the form. Doubt for the lead: `toRequestProfile` drops a blank link row, so a server message for a link
   after a blank row uses the server's (shifted) index and may appear on the wrong row. The UI cannot map it
   back without a server change or a rule not to drop rows. Only ever happens with a blank row before a bad one.
8. The page does not check the other server limits (tag length 60, 30 tags, name 200, and so on). The task
   lists only the ui-profile rules; the server answers them with field messages.
9. Typed text in a tag input that is left without Enter or a comma is not added (not in the spec). Typing
   and then saving loses it silently.
10. The Projects heading uses `heading('projects', ...)` only in 3.4, as the task order says; 3.3 used a plain
    `<h2 id="projects-heading">` until then.
11. The drift test passes because both fixtures parse to the same data. It does not prove the backend's
    `Project` serialises these exact fields; that is the backend round-trip test's job (task 1.3).
