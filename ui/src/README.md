# ui/src: code map

The React 19 + Vite single-page app. It talks only to its own origin: `/api/**` (backend) and `/auth/**` (auth), both
through the gateway. Longer text: `docs/architecture-auth-gateway-ui.md`. Setup notes: `ui/README.md`.

## Folders

- `main.tsx`, `App.tsx`: providers (auth, router) and the route table.
- `auth/`: sign-in with oidc-client-ts (code + PKCE, tokens in memory), `useAuth()`, `/callback`, return-to list.
- `api/`: `apiFetch` (bearer token, one renew and retry on 401, only `/api/...` paths) and fixed error texts.
- `pages/`: `Home` (signed-in overview), `Landing` (signed out).
- `layout/`: `Layout` (skip link, header, `main#content`, footer) wraps every route; `NavBar` is the signed-in top bar,
  `Footer` holds the service status.
- `ui/`: shared components (`Collapsible`, `Card`, `PageHeader`, `Badge`, `Alert`, `EmptyState`, `FormField`).
- `styles/`: `tokens.css` (the only raw colours; light and dark), `base.css`, `components.css`, imported by `index.css`.
- `register/`: account creation, calls `/auth/api/register`.
- `profile/`: the profile form: `types`, `rules` (mirror of backend validation), `fields`, `ProfileForm` (presentation),
  `ProfilePage` (state, save, fill from CV), `OpenSourceSection`, `skills`, API modules, `fixtures/`.
- `cv/`, `files/`, `postings/`, `matches/`, `export/`, `admin/`: one folder per feature, each with a `*Page.tsx`
  and a `*Api.ts` returning result unions (`loaded`, `unauthorized`, `failed`, ...).
- `test/`: `setup.ts`, `fakeAuth.ts`, `fakeManager.ts`, `fakeXhr.ts`.

## Conventions

No store or cache library; each page holds its own state. Pages check `useAuth().state` themselves and show `Landing`
when signed out. Pages render inside the layout's `main`, so their outer element is a `div.page` (`page--narrow` for
forms and reading), never another `main`. Response bodies are never shown to the user.

## Run and test

From `ui/`: `pnpm dev` (port 5173, proxies to the gateway on 8080), `pnpm test` (vitest, jsdom), `pnpm typecheck`,
`pnpm lint`, `pnpm build`. Tests sit next to the code as `*.test.ts(x)` (31 files). `build.test.ts` builds the app and
fails on inline scripts or styles, foreign stylesheets or fonts, `style=` props and raw colours outside `tokens.css`;
`styles/tokens.test.ts` checks the WCAG contrast of the token pairs in both themes. Image: `ui/Dockerfile` (nginx), checked by `ui/test-image.sh`.

Read on 2026-10-24 from: ui/src/**, ui/package.json, ui/vite.config.ts, ui/Dockerfile, ui/nginx/*.
