# Tasks

## 1. Schema

- [x] 1.1 Add Liquibase changeset `004-profile` (table `profile`, foreign key to `app_user(sub)` with `ON DELETE CASCADE`, rollback) and a test that the migration applies on an empty database and that deleting an `app_user` row deletes its profile row.

## 2. Profile module

- [x] 2.1 Public types in `profile`: `Profile` and its nested data classes, enums, `YearMonth`-style month value, `StoredProfile` (profile plus version), `ProfileService`, `ProfileConflictException`, `ProfileInvalidException`; Bean Validation annotations and the limits as constants in one place.
- [x] 2.2 `profile.internal`: repository using `JdbcClient` (read by `sub`; insert-if-absent for version 0; update guarded by `version`), JSON mapping with strict Jackson settings, cross-field checks (dates, workload range).
- [x] 2.3 Tests for the service against the Testcontainers database: empty profile, first save, update, stale save, concurrent saves (exactly one wins), full-field round trip, user A versus user B isolation.

## 3. REST endpoints

- [x] 3.1 `web.internal.ProfileController` for `GET` and `PUT /api/profile` using `CurrentUser`; request size limit (413 above 128 KB, before parsing); exception advice producing 400 `validation_failed` with `fields`, 409 `version_conflict`, never echoing values.
- [x] 3.2 API tests with the mock issuer: 401 without a token, each validation scenario of the spec, 409, 413, unknown field and unknown enum rejected, A cannot see or overwrite B.
- [x] 3.3 Log test: a marker string in every text field never appears in captured logs for a save, a 400 and a 409.
- [x] 3.4 `ModularityTests` still pass; the full backend suite is green.

## 4. UI

- [x] 4.1 `ui/src/profile/types.ts` and `profileApi.ts` (load, save, mapping of 400 and 409 to typed results) with tests, using the shared fixture of a full profile.
- [x] 4.2 `ProfilePage` with sections (location and permit, languages, summary, experience, education, skills, preferences), list add, remove and reorder, dirty tracking and the `beforeunload` guard.
- [x] 4.3 Save flow: field errors at fields, conflict notice with "Reload" that keeps edits until chosen, generic failure message; sign-in return to `/profile` for a signed-out visitor.
- [x] 4.4 Link from the shell, route in `App.tsx`; tests for load, empty profile, add and remove, field error, conflict, no browser storage or console output, guard on and off.
- [x] 4.5 Lint, typecheck, build and the image test (`test-image.sh`) pass.

- [ ] 4.6 `ui/src/profile/rules.ts` with the browser-side rules and tests (each rule, valid and invalid values); the form shows a message on blur and clears it while typing; merge with server messages; focus and scroll to the first field with a message after a failed save; tests for message on leaving a field, clearing, and focus. (Signed-out `/profile` showing the landing page is task 2.2 of the change `silent-sign-in`; the signed-out test in `ProfilePage.test.tsx` changes there.)

## 5. Local run

- [x] 5.1 Run the local stack (auth, backend, gateway, Vite) and use the page in a real browser with a dev user: save, reload, edit, force a conflict from a second tab, check the console is clean. Record the result in `ui/README.md`.

## 6. Deploy and verify

- [x] 6.1 Build the amd64 images `moldovean/job-hunter-backend:0.2.0` and `moldovean/job-hunter-ui:0.2.0`; with the user's go-ahead, push them, set the prod overlay tags, apply to context `s4v3`, and wait for both rollouts.
- [ ] 6.2 Verify on `https://s4v3.net`: `GET /api/profile` is 401 anonymously; the user signs in, fills and saves the profile, reloads and sees it, and the console is clean. Update `k8s/README.md` (image tags) and record the result.

## 7. Wrap-up

- [ ] 7.1 Update `PLAN.md` (the profile section of the module table notes what is built) and archive with `/opsx:archive bootstrap-profile` after 6.2.
