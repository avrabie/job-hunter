# Tasks

## 1. Project scaffold

- [x] 1.1 Install pnpm (`npm i -g pnpm`; Node 25 has no Corepack), create `ui/` from the current Vite React + TypeScript template with versions taken from the npm registry, pin `packageManager` and add `engines`; verify `pnpm install --frozen-lockfile` and `pnpm build` succeed on a clean clone.
- [x] 1.2 Add Vitest, Testing Library, jsdom, oxlint (the current Vite template default, instead of ESLint) and Prettier with `lint`, `typecheck`, `test` and `build` scripts; verify each script runs and `pnpm lint` fails on a deliberately bad line (then remove it).
- [x] 1.3 Add `ui/.gitignore` (`node_modules`, `dist`), `ui/README.md` (install, scripts, dev server) and mention the fourth build in `PLAN.md`; verify the README commands run as written.

## 2. Verify what the design assumes about `auth`

- [x] 2.1 With `curl` against `https://s4v3.net/auth`, request `/oauth2/authorize` with `prompt=none` and no session, and read the discovery document. Result (2026-10-21): `prompt=none` is not honoured (redirect to the login page, same as without it), so the design uses a plain redirect on reload (spec and design amended); `end_session_endpoint` is listed.
- [x] 2.2 Check a refresh-token request for the `spa` client as a public client against the running stack (by registering a throwaway user and using the PKCE helper), and record the access token and refresh token lifetimes in `design.md`; verify the exchange returns a new access token.

## 3. OIDC module and session state

- [x] 3.1 Add `src/auth/config.ts` deriving issuer, redirect and post-logout addresses from `window.location.origin`, and the user manager with an in-memory user store and `sessionStorage` for sign-in state only; verify unit tests cover both a `https://s4v3.net` and a `http://localhost:8080` origin.
- [x] 3.2 Add the React auth context with states `loading`, `signedOut`, `signedIn` and `error`, the `signedIn` hint in `localStorage`, and the start-up restore (a plain sign-in redirect when the hint is set and there are no tokens); verify tests for: no hint shows signed out without a redirect, hint triggers the redirect once, sign-out clears the hint, and a failed callback does not loop back into another redirect.
- [x] 3.3 Add the `/callback` route: complete the sign-in, reject a state mismatch and an error response without showing URL details, remove the code from the address bar; verify tests for success, state mismatch and `access_denied`.
- [x] 3.4 Add renewal and sign-out: automatic renewal with the refresh token, failed renewal discards the user and the hint, sign-out ends the session at `auth`; verify tests with a mocked user manager for each.
- [x] 3.5 Verify no token, email or name reaches `localStorage`, `sessionStorage`, cookies or `console` during a full sign-in flow in a test that spies on all of them.

## 4. Registration

- [x] 4.1 Add the `/register` page and a function posting to `/auth/api/register`; verify tests for 201 (starts sign-in), 409 (email message, values kept), 400 with field errors (messages by field) and a network failure.

## 5. API access

- [x] 5.1 Add `apiFetch` that accepts only `/api/` paths, adds the Bearer token, renews once and repeats once on 401, and signs out if still 401; verify tests for success, renewed retry, second 401, a non-`/api/` path being refused, and no `Authorization` header on a foreign origin.
- [x] 5.2 Map failures to messages (404 "backend has no endpoints yet", 502/503/network "unavailable, retry") without bodies or tokens; verify tests for each status and that the console stays silent.

## 6. Screens

- [x] 6.1 Build the landing page, signed-in shell (identity, sign out, the `/api/` status line) and routing with plain CSS; verify component tests: landing shows both actions and makes no `/api` call, shell shows the email or name and no token text.
- [x] 6.2 Check the production build has no inline script or style (parse `dist/index.html`) and runs under the planned CSP; verify with a test or script in `pnpm test` that fails on an inline script.

## 7. Dev server

- [x] 7.1 Configure the Vite dev server on 5173 with `/api` and `/auth` proxied to `http://localhost:8080`; document the local run in `ui/README.md` and verify, with the other three services running locally, that registering and signing in works at `http://localhost:5173`. (Redirect addresses for 5173 are listed in the `auth` local settings; note them in the README.)

## 8. Image

- [x] 8.1 Write `ui/Dockerfile` (Node 24 build stage, `nginxinc/nginx-unprivileged:1.29.1-alpine` run stage) and `ui/nginx.conf` with fallback, 404 for missing assets and for `/api/` and `/auth/`, cache rules and the four security headers; verify `docker build --platform linux/amd64` succeeds.
- [x] 8.2 Verify the container with `docker run --read-only --cap-drop ALL --user 101`: `/` and `/callback?code=x` return 200 `index.html`, `/assets/missing.js` returns 404, `index.html` is `no-cache`, an asset is `immutable`, and all headers are present. Put these checks in `ui/test-image.sh` so they can be re-run.

## 9. Deploy

- [x] 9.1 Change `k8s/base/ui.yaml` to the image (`job-hunter-ui`), delete the `ui-content` ConfigMap and its mounts, update the local and prod overlays (image name and tag `0.1.0`), and update `k8s/README.md` sections on images, deploying and overlay differences; verify `k8s/validate.sh` and `openspec validate bootstrap-ui --strict` pass.
- [x] 9.2 Push `moldovean/job-hunter-ui:0.1.0` (amd64), confirm an anonymous pull, apply `overlays/prod` to the `s4v3` context and verify the `ui` rollout completes and `curl -sI https://s4v3.net/` shows the new headers.
- [x] 9.3 In a real browser at `https://s4v3.net` with the console open: register a new user, sign in, see the shell with the identity and the backend 404 message, reload and stay signed in, sign out and be signed out after a reload; verify no console errors or CSP violations. Record the result in `ui/README.md`.

## Workflow follow-up

- Archive the change after 9.3 passes (`/opsx:archive bootstrap-ui`).
- Follow-ups recorded elsewhere: persistent `auth` signing key, Google sign-in, OpenAPI client generation once `backend` has endpoints.
