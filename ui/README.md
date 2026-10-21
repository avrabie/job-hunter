# ui

The web UI: React and TypeScript on Vite. It signs users in at our own `auth` service (authorization code + PKCE,
client `spa`) and calls `/api` with the user's token. It is a fourth independent build next to `backend/`, `auth/` and
`gateway/`. Rationale and requirements: `openspec/changes/bootstrap-ui` (or the archived change and the `ui-*` specs).

## Requirements

- Node 22 or newer (developed on Node 25).
- pnpm 12.14.0, the version pinned in `package.json`. Node 25 does not bundle Corepack, so install it with
  `npm i -g pnpm@12.14.0`. With an older Node, `corepack enable` picks the pinned version by itself.

## Commands

Run these in `ui/`.

| Command          | What it does                                                            |
| ---------------- | ----------------------------------------------------------------------- |
| `pnpm install`   | Installs the locked dependencies (`--frozen-lockfile` in CI and Docker) |
| `pnpm dev`       | Dev server on http://localhost:5173                                     |
| `pnpm test`      | Unit and component tests (Vitest, jsdom)                                |
| `pnpm typecheck` | TypeScript, no emit                                                     |
| `pnpm lint`      | oxlint (warnings are errors) and a Prettier check                       |
| `pnpm format`    | Rewrites files with Prettier                                            |
| `pnpm build`     | Type check and production build into `dist/`                            |

`pnpm lint && pnpm typecheck && pnpm test && pnpm build` is what must pass before a commit.

## Local development

The browser must see one origin, as behind the edge in production. `pnpm dev` serves the UI on
http://localhost:5173 and proxies `/api` and `/auth` to the local gateway on port 8080 (`GATEWAY_URL` changes the
target). The UI takes its sign-in addresses from the page's origin, so the issuer that `auth` advertises has to be
`http://localhost:5173/auth` while you work on the UI. The defaults of the other services use the gateway's address
(`http://localhost:8080/auth`), so start `auth` and `backend` with `ISSUER_URI` set. From the repository root, each in
its own terminal (see `gateway/README.md` for the services themselves):

```bash
(cd auth && ISSUER_URI=http://localhost:5173/auth ./gradlew bootRun --args='--spring.profiles.active=dev')
(cd backend && ISSUER_URI=http://localhost:5173/auth ./gradlew bootRun --args='--spring.profiles.active=dev')
(cd gateway && ./gradlew bootRun)
(cd ui && pnpm dev)
```

Open http://localhost:5173, choose Register, create an account and sign in. `backend` reads the issuer lazily through
the dev proxy, so keep `pnpm dev` running while you call `/api`. The `dev` profile of `auth` already allows the
redirect addresses `http://localhost:5173/callback` and `http://localhost:5173/`.

The signed-in page calls `/api/` once and shows "The backend has no endpoints yet." until `backend` has endpoints.
