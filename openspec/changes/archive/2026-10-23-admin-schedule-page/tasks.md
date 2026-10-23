# Tasks

## 1. API client and role check

- [x] 1.1 Add `ui/src/admin/roles.ts` (`hasRole(token, role)`: decodes the payload, tolerates a null, malformed or role-less token by returning false) and `adminApi.ts` (`loadSchedule`, `saveSchedule` with the result kinds `loaded`, `unauthorized`, `forbidden`, `invalid{field,message}`, `failed{message}`, never carrying a response body). Verify with vitest: admin token true, user token false, garbage and null false; 200 maps to `loaded`, 401 to `unauthorized`, 403 to `forbidden`, 400 to `invalid` with the server field and message, 500 to the fixed failure message; the POST body holds only `enabled`, `cron` and `zone`.

## 2. Page and navigation

- [x] 2.1 Add `AdminPage.tsx` (loading, error with retry, enabled/disabled display with next run and last change, form for enabled, cron and zone, save disabled while running, rejected change keeps the typed values and shows the message, 403 shows the administrators-only message with no form), the route `/admin`, and the "Admin" link in `Shell.tsx` shown only when `hasRole(getAccessToken(), 'ADMIN')`. Verify with component tests for every scenario in the `ui-admin` spec: enabled, disabled, 403, signed out (landing page, no request), save, rejected 400, link shown for an admin and hidden for a user, and that no token appears in the DOM or console output.
- [x] 2.2 Style the page in `index.css` consistently with the postings page; run `npx vitest run`, `npx tsc -b && npx vite build`, `npx oxlint --deny-warnings` and `npx prettier --check .` in `ui/` and verify they pass.

## 3. Docs

- [x] 3.1 Document the page and the advisory role check in `ui/README.md`, and add it to `PLAN.md`. Verify the paths and field names match the code.

## Workflow follow-up

- Build and push the UI image (`0.6.0`), bump the tag in `k8s/overlays/prod/kustomization.yaml`, preview with `kubectl --context s4v3 diff -k k8s/overlays/prod`, apply; backend and auth are not touched.
- With the user's go-ahead check on s4v3.net: sign in with Google, open `/admin`, read the schedule, change it and put it back. Note the deploy in `k8s/README.md`.
- Archive the change (new `ui-admin` spec).
