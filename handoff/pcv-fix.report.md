# Public CV review fixes (M1-M4, L1, L3, L5-L7)

Verify results: integrationTest (PublicCvApiTests, PublicCvServiceTests, PublicCvRepositoryTests, PublicCvSchemaTests,
ApiDocsContentTests) 120 tests, 0 failures. Fast tier (WindowLimiterTests, PreviewTicketsTests, RenderCacheTests,
ModularityTests) pass. `compileKotlin compileTestKotlin --rerun-tasks`: no `w:`/`e:` lines. Not committed.
Red run first: 17 failures before the fixes (compile errors for the new signatures counted as red for the cache/limiter tests).

## Findings and proof
- M1 cache: `RenderKey` = sub, profile version, SHA-256 of canonical settings (fixed order, incl. published, template, language);
  `RenderCache.invalidate(sub)` called on save, remove, takedown, unlock. `settingsVersion` is no longer used by the cache
  (column kept, still written; it can restart, nothing relies on it). Tests: PublicCvServiceTests "delete and publish again with
  the email off...", "two saves in a row...", "a profile edit...", "take down and unlock drop...";
  PublicCvApiTests "delete and save again with the email off...", "two saves in a row and a profile edit..."; RenderCacheTests invalidate.
- M2 reclaim: new audit event `HANDLE_CLAIMED` (written on every save without an existing page: first claim and re-claim), changeset
  `moldo:015-...` in new file `010-public-cv-claim.sql` (replaces the check constraint, included in master). Rule: no page, last claim
  (CLAIMED or CHANGED) less than 30 days ago, and the handle is not the owner's most recent held handle -> `ChangeTooSoon`
  (409 `handle_change_too_soon`). Tests: PublicCvServiceTests (different handle refused then allowed after 31 days; same handle
  repeatedly allowed; only most recent handle after change+remove; at most 2 holds at once in the window; first claim audited),
  PublicCvSchemaTests (constraint accepts HANDLE_CLAIMED), PublicCvApiTests (delete then other handle is 409).
- M3 saves limiter: `PublicCvLimits.saves`, `jobhunter.public-cv.saves-per-hour` (30), documented in application.yaml, on PUT (after
  body validation) and DELETE; 429 documented in OpenAPI. Conflict answers unchanged. Threat model rows added to design.md.
  Tests: PublicCvApiTests "the 31st save...", "a taken handle is still told to the owner...".
- M4 limiter: sweep at most once per window, `maxKeys` 20,000 (a new key is refused when full after the sweep; known keys keep
  working). Client IP only from strict IPv4 regex or IPv6 literal (hex/colon/dot chars then `InetAddress.getByName`, no DNS),
  else the socket address. Tests: WindowLimiterTests (100,000 keys, cap, once-per-window), PublicCvApiTests forged XFF (10 junk
  values fall back to the socket), valid IPv6 is used.
- Quick wins: `@Transactional` on save/remove; `Taken` only when the error chain names `public_cv_handle_lower_idx`, else rethrown
  (`isHandleConflict`, tested with a real duplicate and a real check violation in PublicCvSchemaTests); `Locale.ROOT`; HEAD
  permitted for `/api/public/cv/*` only, answers like GET without a body (ApiTests: etag/CSP equal, 404 for unknown, HEAD on
  `/api/cv/public` and preview still 401); ticket redeem no longer scans (PreviewTicketsTests); deleted owner preview is null.
- Docs: edge.yaml comment, edge-routing scenario, cv-public scenarios (Cache never shows older settings, Reclaim after remove,
  Save rate limit), tasks.md group 9 (ticked).

## Changed existing tests (intended behaviour change)
- Audit lists now start with `HANDLE_CLAIMED` (three service tests). The old test "removing and claiming a different handle does
  not reset the 30 day rule" was replaced by the new M2 tests (its fresh claim after remove is now refused).

## Not fixed / notes
- Locale and deleted-owner preview tests pass on the old code too (Kotlin `lowercase()` already uses ROOT; `profiles.get` returns an
  empty profile for a missing user, so the answer was already null/404). Kept as regression tests; changes are cosmetic.
- "At most two holds" is proven within the 30-day window; across months a patient owner can still accumulate holds (one new handle per
  30 days, each held 90 days, so up to 4). No hold cap added; say if you want one.
- On the existing-page path the first claim followed by a change inside 30 days stays allowed (existing tested behaviour); only
  claims after a removal use the claim window.
- Transaction: the unit tests build the service by hand (no proxy), so `@Transactional` is exercised only in the API tests; no
  dedicated atomicity test.
- Not done: L2 (CSP on 404), L4, impersonation words list, U1 (check forged XFF on prod).

## Files
backend/src/main/kotlin/net/s4v3/backend/{accounts/internal/SecurityConfig.kt, cv/internal/PublicCvController.kt,
cv/internal/PublicCvPageController.kt, cv/publiccv/internal/{DefaultPublicCvService,HandleRules,PreviewTickets,PublicCvLimits,
PublicCvRepository,RenderCache,WindowLimiter}.kt}; backend/src/main/resources/{application.yaml, db/changelog/010-public-cv-claim.sql,
db/changelog/db.changelog-master.yaml}; tests PublicCvApiTests, PublicCvServiceTests, PublicCvSchemaTests, WindowLimiterTests,
PreviewTicketsTests, RenderCacheTests; k8s/base/edge.yaml; openspec/changes/public-cv-page/{design.md,tasks.md,specs/cv-public/spec.md,
specs/edge-routing/spec.md}.
