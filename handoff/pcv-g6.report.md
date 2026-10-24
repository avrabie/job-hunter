# pcv-g6 report (task 6.1, profile notice)

Tasks finished: 6.1.

Verify (ui/, real exit codes):
- `npx vitest run`: 32 files, 373 tests passed, exit 0 (profile dir: 8 files, 156 tests).
- `npx tsc -b`: exit 0.
- `npx vite build`: exit 0.
- `npx oxlint --deny-warnings`: exit 0.
- `npx prettier --check .`: exit 0.

Scenarios covered (ProfilePage.test.tsx, "Public CV notice"):
- live: notice with `${origin}/cv/ada-example` and link "Public CV" to /publish.
- not published (configured false): only the "Public CV" link.
- call fails (fetch throws): profile works, no notice.
- slow call (never resolves): profile is editable, no notice.

Loader unit tests (publicCvNotice.test.ts): live, not-live (none, paused), unknown on 500, bad body, connection failure, no token; address builder.

Files changed:
- ui/src/profile/publicCvNotice.ts (new): `PublicCvState`, `loadPublicCvState(auth)` (GET /api/cv/public via apiFetch, never throws), `publicCvAddress(handle)`. This is the local stand-in for `publicCvApi.ts`.
- ui/src/profile/publicCvNotice.test.ts (new)
- ui/src/profile/ProfilePage.tsx: loads the state on mount in parallel, does not wait for it, passes it down.
- ui/src/profile/ProfileForm.tsx: new `publicCv` prop; `PublicCvNotice` rendered in the Contact section.
- ui/src/profile/ProfilePage.test.tsx: `stubApi` answers `/api/cv/public` (404 by default, not recorded in `calls`); new describe block.
- openspec/changes/public-cv-page/tasks.md: 6.1 ticked.

Notes for the lead:
- ProfileForm.tsx and ProfilePage.tsx are in ui/src/profile/ as instructed; the tick touches tasks.md, which is needed for the checkbox.
- Replace `loadPublicCvState` with the shared `getPublicCv` client when group 5 is merged; `publicCvAddress` can stay or move.
- No node_modules or lockfile changes; nothing committed.
