# pub-ui report (tasks 3.1, 3.2)

## Tasks
- 3.1 DONE, ticked. `cd ui && npx vitest run src/profile`: 3 files, 48 tests passed (includes drift test with the backend fixture).
- 3.2 DONE, ticked (see status below).

## 3.2 status
- DONE, ticked. Full UI suite: 22 files, 187 tests, all pass.
- `npx tsc -b`: exit 0. `npx oxlint --deny-warnings`: exit 0. `npx prettier --check .`: clean.
- Lead decision applied: ProfilePage.tsx is unchanged, Save still sends the profile (browser checks are advisory; server is the authority).
  The test "names an unsafe link on leaving it, and shows the server message when saved anyway" checks the message on leaving the
  field, then a stubbed 400 for publications[0].url shows at the link field.

## Files changed
- ui/src/profile/types.ts (Publication, publications on Profile, emptyProfile, blankPublication, toRequestProfile)
- ui/src/profile/rules.ts (publication checks, isHttpUrl helper)
- ui/src/profile/fixtures/full-profile.json (publication sample, prettier-formatted)
- ui/src/profile/profile.test.ts (keys and trim tests)
- ui/src/profile/rules.test.ts (publication rule cases)
- ui/src/profile/ProfileForm.tsx (Publications section after Education)
- ui/src/profile/ProfilePage.test.tsx (three publication tests)
- backend/src/test/resources/profile/full-profile.json (publication sample only, as the Contract says)
- openspec/changes/profile-publications/tasks.md (3.1 ticked)
- handoff/pub-ui.report.md (this file)
- ui/node_modules: symlink to the main checkout's ui/node_modules (worktree had none; gitignored, not committed)

## Surprises and notes
- Messages in rules.ts are my choice; the backend (pub-backend) may word them differently. Server messages win in the form
  (`{...visible, ...serverErrors}`), but the rule text should match. Used: "must not be blank", "must be at most N characters",
  "must be an http or https address", "must be between 1900 and <year+1>".
- The working tree was reported as changed by an external edit on ProfileForm.tsx once; the content was still my section.
- Nothing committed. No cluster, docker, or Anthropic contact.
