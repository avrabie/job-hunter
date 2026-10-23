# Tasks

## 1. Postings read API

- [x] 1.1 Extend the `postings` public API: `Posting.links` (`PostingLink`), `PostingSearch` and `PostingQuery.search`, with links filled for `find`, `list` and `search` (one extra query per page, primary source first), and the SQL search with escaped wildcards. Verify with integration tests for every scenario in the spec: text search on title and company ignoring case, canton filter, source filter, combined filters, newest first-seen order, paging and the 100 cap, two sources giving one posting with two links and the primary first, and `%`, `_` and `\` in the text matching only themselves.

## 2. Endpoints

- [x] 2.1 Add `PostingsController` in `web.internal` with `GET /api/postings` and `GET /api/postings/{id}` (DTOs, validation of `page`, `size`, `q`, `canton`, `source`, 404 for an unknown or malformed id), `@Operation` summaries and responses. Verify with `@ApiTest` tests: no token → 401 on both; a signed-in user gets the list with links and the dates; the detail holds the description and links; unknown id → 404; `page=-1` → 400; `size=1000` returns at most 100; filters work through HTTP; neither answer contains the raw payload (a marker placed in a posting's raw payload never appears).
- [x] 2.2 Add a log-capture test, in the style of `PostingLoggingTests`, asserting a marker sent as `q` never reaches the logs. Verify `ApiDocsContentTests` still passes with the new endpoints and `ModularityTests` passes.

## 3. UI

- [x] 3.1 Add `ui/src/postings/links.ts` (`safeHttpUrl`) and `postingsApi.ts` (list and detail loaders, fixed messages, no response bodies in results). Verify with vitest: `https` and `http` accepted, `javascript:`, `data:` and malformed values refused, query strings built from the filters, 401 mapped to `unauthorized`, 404 to `notFound`, 500 to the fixed failure message.
- [x] 3.2 Add `PostingsPage.tsx` (search, canton and source filters in the URL, paging, loading, empty and error states with retry, one "Open original" anchor per safe link with `target="_blank"` and `rel="noopener noreferrer"`), the route `/postings`, and the "Postings" link in the signed-in shell; a visitor who is not signed in sees the landing page and nothing is requested. Verify with component tests for every scenario in the `ui-postings` spec: list, several sources, empty, signed out, search, paging bounds, unsafe scheme, server error with retry.
- [x] 3.3 Add `PostingPage.tsx` and the route `/postings/:id` (description shown as text with preserved line breaks, links, not-found with a link back). Verify with component tests: a description containing `<script>` and `<b>` appears as text and creates no such elements; an unknown id shows the not-found message.
- [x] 3.4 Run the UI checks (`pnpm test`, `pnpm build` in `ui/`) and verify they pass.

## 4. Docs and integration

- [x] 4.1 Document the endpoints and the page in `backend/README.md` and the UI README, and update the `postings` and `web` rows in `PLAN.md`. Verify the parameter names match the code.
- [x] 4.2 Run the full backend build (`./gradlew build` in `backend/`) and verify it passes.

## Workflow follow-up

- Build and push the backend and UI images, bump both tags in `k8s/overlays/prod/kustomization.yaml`, preview with `kubectl --context s4v3 diff -k k8s/overlays/prod`, apply.
- With the user's go-ahead check on s4v3.net: sign in, open `/postings`, search, follow an original link, open a detail page. Note the deploy in `k8s/README.md`.
- Archive the change (sync `postings` and `ui-postings`), then consider showing match scores on the page.
