# Tasks

Rules for every task (see `handoff/haiku-rules.md`): edit only the files named, copy the pattern of the existing code named, run the verify command, tick the box, stop and report if blocked. Backend commands run in `backend/` (test tiers: `./gradlew test` is the fast tier; API and Spring tests are integration-tagged and run with `./gradlew integrationTest --tests "<Class>"` using double quotes; other agents run Gradle at the same time, so never run the whole suite, only the classes you touch; the lead runs `verify` at the end), UI commands in `ui/` (`npx`; install with `npx --yes pnpm@12.14.0 install --frozen-lockfile` if `node_modules` is missing, never commit it). The Contract in `design.md` is binding. Test data is synthetic (an invented "Ada Example"); never use real personal data. Everything must also work in the Linux amd64 image: no OS-specific tools or fonts.

Groups 1, 2, 5, 6 and 7 are independent and can run in parallel; group 3 needs 1 and 2 merged; group 4 needs 3 merged; group 8 last.

## 0. Checks by the lead (before building)

- [x] 0.1 Find out whether the Traefik version in `k8s/platform/traefik-values.yaml` supports the Gateway API `URLRewrite` filter with `ReplacePrefixMatch` (Traefik docs for that version; do not guess) and record the answer and its source in `design.md` decision 7. If it does not, group 7 changes the UI constant to the direct address and the `edge-routing` delta is dropped.
- [ ] 0.2 Open `/export` on the deployed UI in a browser and check whether the preview is styled or plain (the UI's CSP forbids inline styles and a sandboxed `srcdoc` frame inherits it). Record the result in `design.md` Risks; if the preview is unstyled, add the fix as a follow-up task (same-origin stylesheet link mode for the preview).

## 1. Database and handle rules (backend)

- [x] 1.1 Add Liquibase `backend/src/main/resources/db/changelog/009-public-cv.sql` with the three tables of `design.md` decision 10 (changeset ids `moldo:012-...` following the numbering in the existing files; check the last id used first; rollback statements; all foreign keys `ON DELETE CASCADE`; a unique index on `lower(handle)`) and register it in `db.changelog-master.yaml`. Verify: `./gradlew integrationTest --tests "ProfileSchemaTests"` style schema test: add `PublicCvSchemaTests` (copy `ProfileSchemaTests`/`CvSchemaTests`) that checks the tables, the unique index and that deleting the `app_user` row removes the rows of the three tables.
- [x] 1.2 In `backend/src/main/kotlin/net/s4v3/backend/cv/publiccv/` add `PublicCvSettings` and `PublicCvState` (the Contract types, strict enums), and in `cv/publiccv/internal/` `HandleRules.kt` (`normalize`, `validate` returning an error kind, the reserved list of `design.md` decision 3 as one constant, the all-digits rule) and `ReservedHandlesTests` that also reads the route paths from `ui/src/App.tsx` (as text, `path="/xyz"` patterns) and asserts every first path segment is reserved. Verify: `./gradlew test --tests "HandleRulesTests"` with cases for every scenario of the `cv-public` "Handle" requirement that does not need the database.
- [x] 1.3 Add `cv/publiccv/internal/PublicCvRepository.kt` (JDBC, copy `files/internal/JdbcFileRepository.kt`): load by owner, load by handle, upsert, set published, delete, handle hold insert/check/purge, audit insert, lock/unlock, with the 90-day hold and the 30-day change rule implemented as repository queries taking `now` as a parameter. Verify: `PublicCvRepositoryTests` (integration) for hold, taken, release, change-too-soon with an injected `now`, cascade and audit rows.

## 2. Builder contact visibility (backend)

- [x] 2.1 In `backend/src/main/kotlin/net/s4v3/backend/cv/export/` add `ContactVisibility` (`ALL`, `NONE`) and `ExportOptions.contact` (default `ALL`), and make `internal/CvBuilder.kt` print only the contact items that are on (email, phone, website, LinkedIn, code accounts, location, headline); the full name always. Verify: new cases in `CvBuilderTests` for the three scenarios of `cv-export` "Contact visibility" and a case that `CvExportApiTests`-style default export output is unchanged (compare the builder output with and without the option); `./gradlew test --tests "CvBuilderTests"` and `./gradlew integrationTest --tests "CvExportApiTests"` pass.

## 3. Service (after 1 and 2)

- [ ] 3.1 Add the public `PublicCvService` interface and `RenderedPage` in `cv/publiccv/` and the internal implementation: state, save (validation, name required, locked, hold and change rules, audit with flags only), remove, takeDown, unlock; rendering via `ProfileService.get(sub)`, `CvBuilder` with the settings turned into `ExportOptions` (sections, `includeAnalysis`, `includeWorkAuthorisation`, `ContactVisibility`), `HtmlRenderer`, a robots meta and the page's CSP meta; ETag = SHA-256 of the HTML; a render cache keyed by `(sub, profile version, settings version)` with at most 500 entries and 10 minutes (small own class, injectable clock). Verify: `PublicCvServiceTests` for the rules of `cv-public` "Visibility settings", "Publish, pause and remove" and "The public page is live" that need no HTTP (defaults private, ticked items, preferences never printed with a salary in the fixture, live edit shows, unticked edit stays private, cache invalidates on version change).
- [ ] 3.2 Add `WindowLimiter(limit, window, clock)` and the three public-page limits, the owner-endpoint limits, and the preview ticket store (one use, 60 s, bound to owner and settings, injectable clock) in `cv/publiccv/internal/`. Verify: plain unit tests for each (window edges, per-key separation, ticket reuse and expiry).

## 4. Endpoints (after 3)

- [ ] 4.1 Add `PublicCvController` (management: GET/PUT/DELETE `/api/cv/public`, handle check, preview POST and ticket GET) and `PublicCvPageController` (`GET /api/public/cv/{handle}` with the headers of `cv-public` "Safe headers and content", ETag/304, client IP from `X-Forwarded-For`, uniform 404, 429 with `Retry-After`), strict JSON like `CvExportController`, OpenAPI annotations, fixed error bodies from the Contract, and the admin endpoints under `/api/admin/public-cv/`. In `accounts/internal/SecurityConfig.kt` add `requestMatchers(HttpMethod.GET, "/api/public/cv/*", "/api/cv/public/preview/*").permitAll()` before the authenticated rule (explicit patterns only). Add a daily `@Scheduled` purge of expired holds. Verify: `PublicCvApiTests` (integration) for every scenario of `cv-public` (handle, settings, publish/pause/remove, live page, uniform 404, no other public path, headers, XSS fixture, 304, limits, preview, admin takedown with a 403 for non-admins, audit, deletion cascade, nothing in logs using `LogCapture`) ; then `./gradlew build` and `./gradlew integrationTest --tests "ModularityTests"`-equivalent (`ModularityTests` is fast tier) and `ApiDocsContentTests` pass (every endpoint documented).

## 5. UI Public CV page

- [x] 5.1 Add `ui/src/publiccv/publicCvApi.ts` (get, put, delete, handleCheck, preview with fixed messages per error code; copy `exportApi.ts`/`analysisApi.ts`), `options.ts` (types of the Contract, all-off defaults) and tests. Verify: `npx vitest run src/publiccv`.
- [x] 5.2 Add `ui/src/publiccv/PublicCvPage.tsx` per the `ui-cv-public` requirements "Public CV page", "Handle field", "Save, publish, pause and remove" and "Preview" (debounced availability check, sections with move up/down, live banner with copy button, preview opens a new tab, no rendered HTML in the DOM), the route `/publish` in `App.tsx`, a link in `Shell.tsx`, a constant `PUBLIC_CV_PATH` (`/cv/`), CSS only for the new page. Verify: `PublicCvPage.test.tsx` for every scenario of those requirements; then `npx vitest run && npx tsc -b && npx vite build && npx oxlint --deny-warnings && npx prettier --check .`.

## 6. UI profile notice

- [x] 6.1 In `ui/src/profile/ProfileForm.tsx`/`ProfilePage.tsx` show the live notice or the "Public CV" link per `ui-cv-public` "Profile page shows the live state" (one `getPublicCv` call that must not block or fail the page; import the function from `ui/src/publiccv/publicCvApi.ts` once group 5 is merged, until then a local type and a stub in tests). Verify: tests for the three scenarios; the full UI check chain passes.

## 7. Edge route (after 0.1)

- [ ] 7.1 In `k8s/base/gateway.yaml` (the HTTPRoutes) add the rule of `design.md` decision 7: PathPrefix `/cv/`, filter `URLRewrite` with `ReplacePrefixMatch: /api/public/cv/`, backend the gateway Service; update `k8s/validate.sh` expectations if it checks routes, and the `edge-routing` text in `k8s/README.md`. Verify: `k8s/validate.sh` passes (it renders and schema-checks both overlays); show the rendered rule in the report. The lead applies it with `kubectl diff` first.

## 8. Docs

- [ ] 8.1 Document the endpoints, settings, limits and the privacy model in `backend/README.md` (section "Public CV"), the page in `ui/README.md`, a new `docs/cv-public.md` (what is public, what never is, how to take a page down as an admin, the live-page trade-off, retention and deletion, the audit table, the threat model of `design.md`) and one line in `PLAN.md`. Verify: `grep -n -i "public cv" backend/README.md ui/README.md PLAN.md` shows each file and `docs/cv-public.md` exists.

## Workflow follow-up

- The lead reviews each diff, merges, runs `./gradlew clean verify` and the UI checks, runs the 0.x checks, deploys backend, UI and the route together (`kubectl diff` first), tests on s4v3.net with a real account and an incognito window (published page, pause, handle change, 404s, headers with `curl -I`), then archives the change. Follow-ups: `public-cv-extras` (PDF download, Open Graph, snapshot mode), the inline-preview/stylesheet fix if 0.2 shows it is needed.
