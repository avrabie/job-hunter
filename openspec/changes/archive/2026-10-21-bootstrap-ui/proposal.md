# Proposal

## Why

The platform runs at `https://s4v3.net` (edge, gateway, `auth`, `backend`, Postgres), but the page at `/` is a placeholder served from a ConfigMap, and nothing in the repo can sign a user in from a browser. Every later screen (profile, postings, review of drafts) needs the same base: a build, a login against our own `auth`, an API client that carries the token, and an image that deploys. This change builds that base and nothing else, so the product screens can be added on top of a proven path.

## What Changes

- Add a `ui/` project, a fourth independent build next to `backend/`, `auth/` and `gateway/`: React and TypeScript on Vite, with its own tests, lint and README.
- Sign in with authorization code + PKCE against `auth` (client `spa`), using the refresh token the client already supports. Tokens stay in memory only; a page reload signs in again silently through the session `auth` keeps.
- A registration form that calls `POST /auth/api/register` and then starts the sign-in, because `auth` has no registration page of its own.
- A signed-in shell that shows who is signed in and has a sign-out that also ends the session at `auth`; signed-out visitors see a landing page with sign-in and register.
- A small API client that sends the Bearer token on `/api/...` calls and starts a new sign-in on a 401. The shell calls `/api/` once and shows "backend has no endpoints yet" for its 404, which is the live proof of the whole path.
- A container image (nginx, non-root, read-only) that serves the build with SPA fallback and security headers, replacing the ConfigMap page in `k8s/base/ui.yaml`; the production overlay pins its tag.
- Local development: the Vite dev server on port 5173 proxies `/api` and `/auth` to the local gateway on 8080, so the browser sees one origin as in production.
- Deploy to `s4v3.net` and verify a real sign-in in a browser.

**Explicitly out of scope** (later changes): any product screen (profile, postings, applications, settings); a client generated from OpenAPI (needs backend endpoints first); Google sign-in (needs the OAuth client in `auth`); password reset and email verification; internationalisation (EN/DE UI text); a component library or design system; CI; end-to-end browser tests in CI; any change to `backend`, `auth` or `gateway` code.

## Capabilities

### New Capabilities
- `ui-sign-in`: how a visitor registers, signs in, stays signed in across a reload, and signs out; where tokens live; what a signed-out visitor can see.
- `ui-api-access`: how the UI calls `/api` with the user's token, what it does on 401, and what it never sends or stores.
- `ui-delivery`: how the UI is built, tested, run locally, packaged and served, and which headers it sends.

### Modified Capabilities
- `workload-deployment`: the `ui` workload changes from a ConfigMap placeholder to the real image.

## Impact

- **Modules touched:** none of the backend's Modulith modules. New `ui/` build; `k8s/base/ui.yaml` and `k8s/overlays/*` change; `k8s/README.md` and `PLAN.md` mention the fourth build.
- **Dependencies (new, `ui/` only):** React, `react-router`, `oidc-client-ts`, Vite, Vitest with Testing Library, oxlint, Prettier. No state or data-fetching library yet; see `design.md`.
- **Tooling:** pnpm (Corepack is not bundled with Node 25, so it is installed with npm), Node LTS.
- **Personal data:** the UI handles the user's email and name from the ID token and holds tokens in memory. It writes no token or personal value to `localStorage`, `sessionStorage` or cookies (only the short-lived sign-in state and a boolean flag, see `design.md`) and logs no token or personal value. CV content does not exist in the UI yet.
- **Per-user scoping:** unchanged; the UI sends only its own user's token and the backend enforces scoping by `sub`.
- **Claude cost:** none.
- **External systems:** `auth` (existing `spa` client and `/auth/api/register`), Docker Hub for the image.
