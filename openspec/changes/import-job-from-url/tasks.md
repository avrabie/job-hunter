## 0. Lead checks

- [ ] 0.1 Pin the jsoup version from Maven Central and check it works in the linux/amd64 image.
- [ ] 0.2 Owner confirms the assumptions: no robots.txt check for one user-initiated fetch, no admin removal yet, limits 20 previews/hour and 10 confirms/day.

## 1. Fetcher (independent)

- [x] 1.1 `jobimport/internal/UrlRules` (https/443/no userinfo/no IP literal, canonical URL, denylist) with unit tests.
- [x] 1.2 `AddressRules` (public-address check incl. IPv4-mapped and ULA) and `SafeFetcher` (validated-address connect with SNI, manual redirects, 10 s, 1 MB, identity encoding, content type), tested against a local TLS fake with rebinding, redirect-to-internal, oversize and wrong-type cases.
- [x] 1.3 `PageText` (jsoup to capped plain text, under-300-chars rule) with tests.

## 2. LLM extractor (independent)

- [x] 2.1 `llm.JobExtractor`, `KoogJobExtractor`, prompt, output cleaning, `JobImportSettings`, tests with `FakePromptExecutor` incl. injection text and HTML output.

## 3. Postings read model and schema

- [x] 3.1 Liquibase `moldo:018-job-import` (`job_import_draft`, `posting_import`); update `DatabaseBaselineTests`.
- [x] 3.2 `PostingQuery.userAdded`; tests.

## 4. Service and API (after 1, 2, 3)

- [x] 4.1 `JobImportService` (preview, paste mode, grounding check, draft store with TTL and purge, confirm via `PostingIngest` and `ApplicationService`, attribution row, limiter) and `JobImportController`; API tests incl. user A/B, expired draft, dedupe, limits, no URL/text in logs, account deletion cleanup.
- [x] 4.2 `NetworkPolicy` for the backend pod denying private and metadata egress; `k8s/validate.sh`.

## 5. UI (after 4.1 contract)

- [x] 5.1 `ui/src/applications/ImportJob*` form, review step, paste fallback, error messages; tests.
- [x] 5.2 "Added by a user" badge in `PostingsPage` and `PostingPage`; tests.

## 5b. Rework after the security review (owner chose private imports)

- [ ] 7.1 Backend: remove shared-posting creation (`PostingIngest` use, `posting_import`, `userAdded` in postings read model and API, related tests); edit changeset `moldo:018` in place (drop `posting_import`, add `application.source`, nullable `posting_id`, unique index on (owner, url) for imports); `ApplicationService.trackImported`; live status OPEN for imports; confirm returns `{applicationId, created}`; fix the review's M2, M3, M4 and Low items listed in design decision 7; update tests.
- [ ] 7.2 UI: remove the "Added by a user" badge and `userAdded` type; update the review-form notice (the job stays private to you) and the confirm result handling (`created`).

## 6. Wrap-up

- [ ] 6.1 Merge, full `./gradlew clean verify`, UI tests/tsc/oxlint/prettier, `openspec validate --all`, independent security review (SSRF, rebinding, redirects, injection, logs, limits), docs (`docs/architecture.md`, `docs/glossary.md`, module README), deploy only after the owner says so; archive after the owner tests.
