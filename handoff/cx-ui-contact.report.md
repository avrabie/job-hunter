# Report: cx-ui-contact (task 5.1, UI contact section)

## Tasks

- 5.1 verified. Verify command `npx vitest run src/profile && npx tsc -b`: 7 files, 147 tests passed in `src/profile`; `tsc -b` exit 0.
- Tick NOT made in `openspec/changes/export-profile-cv/tasks.md`: that file is outside the files I was allowed to edit (ui/src/profile/ only). The lead should change `- [ ] 5.1` to `- [x] 5.1`.
- 6.x, 7.x: not touched.

## Full check chain (ui, this worktree)

- `npx vitest run`: exit 0, 28 files, 329 tests passed, 0 failed.
- `npx tsc -b`: exit 0.
- `npx vite build`: exit 0 (dist/ built, not committed).
- `npx oxlint --deny-warnings`: exit 0, no output.
- `npx prettier --check .`: exit 0, all files formatted.

## Files changed (all under ui/src/profile/)

- `types.ts`: `Contact` interface (six `string | null` fields), `Profile.contact` as first field, all-null default in `emptyProfile`, `toRequestProfile` trims and sends empty as null, contact block first in the request object.
- `rules.ts`: `isHttpsUrl`, `EMAIL` and `PHONE` patterns (mirror backend rules). `checkProfile` adds `contact.fullName` and `contact.headline` (max 200), `contact.email` (max 200 and shape), `contact.phone` (max 30 and charset), `contact.website` and `contact.linkedin` (max 500, https). Messages never contain the value.
- `ProfileForm.tsx`: new first section "Contact" (`aria-labelledby="contact-heading"`): hint "The full name is needed to export a CV." with `<Link to="/export">Export CV</Link>` (react-router `Link`, same as PostingsPage). Six `TextInput`s with paths `contact.*`, labels Full name, Headline, Email, Phone, Website, LinkedIn.
- `fixtures/full-profile.json`: `"contact"` as first field with the Contract's values (Ada Example, synthetic). Formatted by prettier.
- `rules.test.ts`: new `describe('checkProfile contact')`: email, phone, website, LinkedIn, length limits, empty block.
- `profile.test.ts`: contact keys in the "types match" test; new `describe('contact block')` (trim/null, sample unchanged).
- `ProfilePage.test.tsx`: new `describe('Contact section')`: fill and save (PUT body and reload shows the same values), bad email named on leave with no PUT, server message at the email field after save, hint and link to `/export`.

## Notes for the lead

- Dependencies: the worktree had no `node_modules`. `npm ci` fails (the project has `pnpm-lock.yaml`, no `package-lock.json`). I ran `npx --yes pnpm@12.14.0 install --frozen-lockfile` in `ui/` (version from `packageManager`). No lockfile changed (git status shows only the 7 source files).
- The `/export` route does not exist in this worktree yet; the link renders and is tested as an href only.
- Old profiles: the UI relies on the server always sending `contact` (Contract: always present). No client fallback was added for a response without it.
- Phone check: the length message is reported by the length check; the charset regex has no length bound (so a 31-char phone shows the length message, not the charset one).
- Backend fixture (`backend/src/test/resources/profile/full-profile.json`) is changed by another agent; the UI `profile.test.ts` key test only checks the UI fixture.
