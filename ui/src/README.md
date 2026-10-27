# ui/src: code map

The React 19 + Vite single-page app. It talks only to its own origin: `/api/**` (backend) and `/auth/**` (auth), both
through the gateway. Longer text: `docs/architecture-auth-gateway-ui.md`. Setup notes: `ui/README.md`.

## Folders

- `main.tsx`, `App.tsx`: providers (auth, router) and the route table. Signed-in and signed-out pages share one `Layout` route.
- `auth/`: sign-in with oidc-client-ts (code + PKCE, tokens in memory), `useAuth()`, `/callback`, return-to list.
- `api/`: `apiFetch` (bearer token, one renew and retry on 401, only `/api/...` paths) and fixed error texts.
- `pages/`: `Home` (signed-in overview with counts per stage), `Landing` (signed out; its copy is in `landingContent.ts`, a data file; `LatestPostings` is its optional section of up to 6 public postings, loaded without a token by `postings/publicPostingsApi.ts`, absent on failure, 429 or empty; the only `/api` call the landing makes).
- `layout/`: `Layout` (skip link, header, `main#content`, footer) wraps every route. `NavBar` is the signed-in top bar
  (`BrandMark` too); the signed-out header is a small `PublicHeader` inside `Layout`. `Footer` holds the service status.
- `ui/`: shared components, see "Shared components" below.
- `styles/`: the design system, see "Design tokens and styles" below.
- `register/`: account creation, calls `/auth/api/register`.
- `profile/`: the profile page. `ProfilePage` (state, save, fill from CV), `ProfileForm` (presentation), one
  `*Section.tsx` per section wrapped in `ProfileSection` (a `Collapsible`), `SkillsSection` with `skillGroups.ts`,
  `types`, `rules` (mirror of backend validation), `fields`, `OpenSourceSection`, `skills`, API modules, `fixtures/`.
- `cv/`, `files/`, `postings/`, `matches/`, `export/`, `publiccv/`, `applications/`, `admin/`: one folder per feature,
  each with a `*Page.tsx` and a `*Api.ts` returning result unions (`loaded`, `unauthorized`, `failed`, ...).
- `test/`: `setup.ts`, `fakeAuth.ts`, `fakeManager.ts`, `fakeXhr.ts`.

## Design tokens and styles

`styles/tokens.css` is the only file with raw colour values. It defines custom properties on `:root`: colours
(`--color-bg`, `--color-surface`, `--color-text`, `--color-muted`, `--color-primary`, `--color-border`,
`--color-border-strong`, `--color-danger`, ...), type (`--font-sans`, `--text-xs` to `--text-3xl`, `--weight-*`), a 4-point
spacing scale (`--space-1` to `--space-16`), radii, shadows, focus ring width and offset, `--touch-target`, the content
widths `--content-max` and `--content-narrow`, and `--duration`. Dark values sit under
`@media (prefers-color-scheme: dark)`; there is no manual theme switch. `--duration` is 0 with reduced motion.

Files, in load order:

- `tokens.css`: the tokens above, light and dark.
- `base.css`: element defaults (body, headings, links, focus ring), built from tokens only.
- `components.css`: shared classes: buttons (`.button`, `--secondary`, `--ghost`, `--danger`, `--link`), form fields,
  skip link, header and nav, footer, `page`, `page-header`, cards, badges, alerts, empty states, collapsible sections,
  the landing page and the profile section and skill rows.
- `index.css`: imports the three files above and keeps the legacy page rules (`form label`, `.actions`, `.tag-list`,
  ...) that the profile page and the file cabinet still use. A legacy rule goes when no page uses it.
- Per-page files, each imported by its page component (so they load with that page): `account.css` (`CallbackPage`),
  `admin.css` (`AdminPage`), `applications.css` (job tracking pages: list, import, application, letter, interested
  button), `export.css` (`ExportPage`), `matches.css` (`MatchesPage`), `postings.css` (postings list and posting page),
  `publiccv.css` (`PublicCvPage`). They use tokens only and follow the `block__element--modifier` naming.

`styles/tokens.test.ts` computes the WCAG contrast of the text, control and focus pairs in both themes (4.5:1 for text,
3:1 for control edges and the focus ring).

## Shared components (`ui/`)

- `Collapsible` (+ `CollapsibleGroup`, `ExpandCollapseAll`): see the next section.
- `PageHeader`: the page's one `h1`, an optional lead and page actions. Every page starts with it.
- `Card`: a bordered surface; with a `title` it is a labelled `section`, without one a plain box.
- `Badge`, `Alert` (`info`, `success`, `warning`, `danger`), `EmptyState`: small status and empty-state blocks.
- `FormField`: a label, control, hint and error wired with `id`, `aria-describedby` and `aria-invalid`. The profile
  form keeps its own `profile/fields.tsx` (its tests rely on the label-wraps-input markup).
- `revealAndFocus`: opens every closed `details` around an element and then focuses it. A form that focuses its first
  invalid field uses it, because content of a closed `details` cannot take focus.

## Collapsible sections

`Collapsible` is a `details`/`summary`, so the browser gives keyboard operation (Enter and Space) and the expanded
state. Its open state comes from, in order: the `open` prop (controlled), the enclosing `CollapsibleGroup` when the
section has an `id`, or its own state starting at `defaultOpen`. `summary` is one line next to the title (a count or
status). `titleAs` sets the heading level inside the summary, so the page outline holds while the section is closed.
`forceOpen` opens the section whenever it turns true, for a section with an error or a filter that matches inside it.

`CollapsibleGroup` with `ExpandCollapseAll` gives "Expand all" and "Collapse all" for the sections inside it. Both
reset each section's own choice. The profile form wraps all its sections in one group. Closing a section only hides
its fields; they stay mounted, so nothing is lost or saved differently.

## Skill groups (`profile/skillGroups.ts`)

Skills are sorted into nine groups, in display order: `LANGUAGES`, `FRAMEWORKS`, `MESSAGING`, `SECURITY`, `CLOUD`,
`TESTING`, `DATABASES`, `TOOLS`, `OTHER` (last). `groupSkills` matches each skill name against keyword lists (the
first matching group wins; no match is `OTHER`). `SKILL_GROUPS` and the keywords are ported from the backend's
`SkillGroups.kt`, which is the reference. The shared case table `skillGroups.cases.json` is also read by the backend's
`SkillGroupsTests`, so the two cannot drift silently.

`SkillsSection` renders one `Collapsible` per non-empty group, titled with `SKILL_GROUP_LABELS` and summarised with a
count. Groups start open with 20 skills or fewer and closed above that. A filter, or an error inside a group, forces
that group open. The filter box and "Add skill" sit above the groups.

## How to add a page

1. Create `<feature>/<Name>Page.tsx` and, if it calls the backend, `<feature>/<name>Api.ts` returning a result union.
2. Read `useAuth().state` in the page. When signed out, render `<Landing returnTo="/your-path" />` (the return list in
   `auth/returnTo.ts` allows only `/` and `/profile`, so other paths land on `/` after sign-in).
3. Outer element `<div className="page">` (or `page page--narrow` for forms and reading) with one `PageHeader`. Never a
   nested `main`.
4. Add the route in `App.tsx`, inside the `Layout` route. Add a `NavLink` in `layout/NavBar.tsx` when it belongs in the
   top bar.
5. If the page needs its own look, add `styles/<feature>.css` (tokens only) and import it from the page component.
6. Tests next to the code (`<Name>Page.test.tsx`), using `fakeAuth` and a stubbed `fetch` as the other pages do.

## Content Security Policy rules

The served page sends `script-src 'self'` and `style-src 'self'` with no `unsafe-inline` (`ui/nginx/security-headers.conf`).
So:

- No inline `<style>`, no `style=` props, no inline `<script>`, no event-handler attributes, no
  `dangerouslySetInnerHTML`. Put the look in a CSS class and the state in a class modifier or a `data-*` attribute.
- Icons are inline SVG (`BrandMark`, the landing icons); no icon font.
- Fonts are files of our own or the system stack (the current choice). No font or stylesheet from another origin.
- Raw colours only in `tokens.css`.

`build.test.ts` checks these on a real build: no inline script or style in the output, same-origin stylesheets only,
no `style=` or `<style` in components, no raw colours outside `tokens.css`. A change that breaks a rule fails `pnpm test`.

## Conventions

No store or cache library; each page holds its own state. Pages check `useAuth().state` themselves and show `Landing`
when signed out. Pages render inside the layout's `main`, so their outer element is a `div.page` (`page--narrow` for
forms and reading), never another `main`. Response bodies are never shown to the user.

## Run and test

From `ui/`: `pnpm dev` (port 5173, proxies to the gateway on 8080), `pnpm test` (vitest, jsdom), `pnpm typecheck`,
`pnpm lint`, `pnpm build`. Tests sit next to the code as `*.test.ts(x)` (51 files). `build.test.ts` builds the app and
fails on the CSP rules above; `styles/tokens.test.ts` checks the WCAG contrast of the token pairs in both themes. Image:
`ui/Dockerfile` (nginx), checked by `ui/test-image.sh`.

Read on 2026-10-25 from: ui/src/**, ui/package.json, ui/vite.config.ts, ui/Dockerfile, ui/nginx/*.
