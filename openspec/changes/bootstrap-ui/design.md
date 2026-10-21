# Design

## Context

See `proposal.md` for the motivation. What exists today, observed in the repo and on the cluster:

- `auth` has one public client, `spa`: authorization code with PKCE required, refresh tokens (also for public clients, through a small extension), scopes `openid profile email`, no consent screen. Its redirect and post-logout addresses come from `APP_SPA_REDIRECT_URIS` and `APP_SPA_POST_LOGOUT_REDIRECT_URIS`; production already allows `https://s4v3.net/callback` and `https://s4v3.net/`. Its issuer is `<public origin>/auth`. It serves its own login page, but registration is only a JSON endpoint, `POST /auth/api/register`.
- The edge sends `/` to `ui`, and `/api` and `/auth` through the Spring gateway, all on one origin, so the UI needs no CORS. The Spring gateway's secure-headers filter has its content security policy turned off for the `auth` login page, so the UI must set its own.
- `ui` is an `nginx-unprivileged` pod with a ConfigMap page, `readOnlyRootFilesystem`, a `/tmp` volume and probes on `/`.
- `backend` has no endpoints; `/api/` answers 404 with a valid token and 401 without one.
- Node 25 and npm 11 are installed locally; pnpm is not.

## Goals / Non-Goals

**Goals:**
- One proven path from a browser to a token to a backend call, deployed.
- The same image on every host: nothing environment-specific baked in.
- Small enough that the product screens can add code without redoing the foundation.

**Non-Goals:**
- Any product screen, design system, or translation of UI text.
- A server-side session or a backend-for-frontend; the PLAN keeps the gateway free of tokens and sessions.
- Offline support, service workers, or server-side rendering.

## Decisions

**React + TypeScript on Vite, `react-router`, plain CSS.** Decided in `PLAN.md`. Routing is only `/`, `/register` and `/callback` for now. Plain CSS because there is no design yet; a library can be added with the first real screen. *Alternatives:* Next.js (a server we do not want to run), Tailwind (a choice better made with a design).

**`oidc-client-ts` for the protocol, wrapped in one small module.** It does PKCE, state checks, discovery, refresh and RP-initiated logout, so none of that is hand-written. All use goes through one `auth` module and a React context, so a later change touches one place. *Alternative:* hand-rolled PKCE: more code on a security-critical path for no gain. It is the only runtime dependency beyond React and the router.

**Tokens in memory only.** The user store (tokens, profile) is `oidc-client-ts`'s in-memory store. The short-lived sign-in state (random `state` and PKCE verifier) must survive the redirect to `auth` and back, so it uses `sessionStorage`; it holds no token and no personal data and is deleted when the callback completes. *Alternative:* keeping tokens in `sessionStorage`: simpler, but any script injected by XSS could read them. The cost of memory-only is the reload behaviour below.

**Reload restores the session with a redirect, guided by a boolean hint.** A reload loses the in-memory tokens, but the session at `auth` still exists. The UI keeps one flag in `localStorage` (`signedIn=1`, no token, no identity), set after sign-in and cleared on sign-out. At start, with the flag set and no user, it starts the normal sign-in redirect; `auth` returns a code at once when its session is valid, and shows its sign-in form when it is not. Visitors without the flag never bounce and see the landing page. *Alternatives:* a hidden iframe with `prompt=none` (the usual silent flow) is blocked by the secure-headers filter's frame deny on `/auth`; `prompt=none` as a redirect was tried on 2026-10-21 and `auth` does not honour it (it redirects to its login page exactly as without the parameter, because the login redirect fires before the authorization endpoint evaluates `prompt`), so the UI does not use it. Making `auth` answer `prompt=none` with `login_required` is a possible follow-up for `auth`; it would let an expired session end on the landing page instead of the sign-in form. A redirect on every visit, without the flag, would add a round trip for new visitors.

**Renewal with the refresh token.** `oidc-client-ts` renews shortly before expiry and uses the refresh token when one exists, so no iframe is needed. A failed renewal clears the in-memory user and the hint. Measured on `https://s4v3.net` (2026-10-21): the access token lives 15 minutes (`expires_in` 899), the refresh token 60 minutes and it rotates, so every renewal returns a new refresh token that replaces the old one in memory. A refresh request with only `client_id` and the refresh token works for the public client.

**Configuration from `window.location.origin`.** Issuer is `<origin>/auth`, redirect is `<origin>/callback`, post-logout is `<origin>/`. The image therefore has no per-environment build step, and the dev server (proxying to the gateway) behaves like production. *Alternative:* build-time `VITE_*` variables: one image per environment, which the cluster layout does not need.

**One API function that owns the Bearer header and the 401 rule.** `apiFetch(path)` adds the token, on 401 renews once and repeats once, then signs out. It only accepts paths under `/api/`, so the token cannot be sent to another origin by mistake. No data-fetching library yet: the shell makes one call. Add TanStack Query when there are lists and mutations.

**Registration page in the UI, posting to `/auth/api/register`.** The endpoint is already public and returns 201, 409 or 400 with `{"error","fields"}`. After a 201 the UI starts the normal sign-in; the user types the credentials once more at `auth`. *Alternative:* pre-filling or auto-login after registration needs a password flow that `auth` does not have and we do not want.

**nginx serves the build, with fallback and headers in its own config.** `try_files $uri /index.html` for app paths, `404` for missing files under `/assets/`, and explicit `404` under `/api/` and `/auth/` (the edge never sends them here, but a misrouted request must not get HTML). Headers: `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: same-origin`, `X-Frame-Options: DENY`. Because the policy forbids inline scripts and styles, Vite's output must have none; the build is checked for that. `index.html` is `no-cache`; `/assets/*` (content-hashed) are `immutable`, one year. The config is baked into the image, so the ConfigMap in `k8s/base/ui.yaml` goes away.

**Image: multi-stage, `nginxinc/nginx-unprivileged` as the base, built for amd64.** Same base image and tag as the placeholder, so the pod's existing hardening (non-root, read-only root, `/tmp` volume) keeps working. Tag `moldovean/job-hunter-ui:0.1.0`, pushed by hand like the other three images.

**Tooling.** pnpm (installed with `npm i -g pnpm`, since Node 25 no longer bundles Corepack) with the version pinned in `package.json`, a committed lockfile, Vitest with Testing Library and jsdom, oxlint (the Vite template default; fast, no plugin setup) with `--deny-warnings`, Prettier. Versions are taken from the npm registry when scaffolding, not guessed. Node 24 (LTS) in the Dockerfile; local Node 25 works for development.

## Risks / Trade-offs

- [An expired session lands on the `auth` sign-in form, not the landing page] → accepted; it is where a returning user wants to be anyway. `auth` supporting `prompt=none` is a possible follow-up.
- [Memory-only tokens mean a reload costs a redirect round trip] → accepted; typically under a second, and only for returning users.
- [`auth` restart invalidates every token (key generated at startup)] → the UI treats a rejected token or failed renewal as signed out, so the user lands on the landing page and signs in again; the persistent key stays a follow-up for `auth`.
- [The CSP breaks the app in ways unit tests cannot see] → verify in a real browser against the deployed site with the console open (task 9.3), not only with `curl`.
- [Refresh token in JavaScript memory is still reachable by an XSS bug] → no `eval` or inline script allowed by the CSP, React escapes by default, and the dependency list is short; the refresh token lifetime is `auth`'s setting.
- [The `localStorage` hint is readable by any script on the origin] → it reveals only that someone signed in on this browser.
- [pnpm is not installed here] → `npm i -g pnpm@<pinned version>` (Node 25 has no Corepack); the README says so.

## Migration Plan

1. Build and push `moldovean/job-hunter-ui:0.1.0` (amd64), check an anonymous pull.
2. Change `k8s/base/ui.yaml` (image, no ConfigMap) and the prod overlay tag; run `k8s/validate.sh`.
3. `kubectl --context s4v3 apply -k k8s/overlays/prod`; the `ui` Deployment rolls with the existing rolling update.
4. Rollback: re-apply the previous commit's manifests, which bring back the ConfigMap page; nothing else depends on the UI.

## Open Questions

- Which pages the first real change after this one builds (profile and CV upload, most likely). It does not affect this change.
