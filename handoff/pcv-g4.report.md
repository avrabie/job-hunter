# public-cv-page, group 4 (task 4.1), backend

## Result
4.1 done and ticked. Verify: integration `PublicCvApiTests` 41, `ApiDocsContentTests` 6, `CvExportApiTests` 14, `FilesApiTests` 28, `ProfileApiTests` 14: all pass (103 run). Fast tier `ModularityTests` 2, `WindowLimiterTests` 7: pass. `compileKotlin compileTestKotlin --rerun-tasks`: no `w:`/`e:`. `./gradlew build` not run (others use Gradle).

## Endpoints (all in `cv/internal/`, internal)
- `PublicCvController`: GET/PUT/DELETE `/api/cv/public`, GET `/api/cv/public/handle?handle=`, POST `/api/cv/public/preview`. Strict JSON (unknown field, unknown enum, null section, null flag, missing handle: 400 `invalid_request`). Save errors per Contract (`validation_failed` + `fields`, 409 codes). DELETE: REMOVED and NOT_FOUND 204, LOCKED 409 `locked`. Handle checks and previews limited per sub (429 `rate_limited` + `Retry-After`).
- `PublicCvPageController`: GET `/api/public/cv/{handle}` and `/api/cv/public/preview/{ticket}`. Order as instructed (junk handle: only `notFoundByIp`; plausible: `pageByIp`, `pageByHandle`, render, `notFoundByIp` on null). 404/429 carry `no-store`, `noindex`, nosniff, no-referrer. ETag quoted, `If-None-Match` (list, `W/`, `*`) gives 304 without body but with headers. Preview: `no-store`, always noindex, no IP limits (ticket is 128 bits), ticket not 32 hex gives 404 without a lookup.
- `PublicCvAdminController`: DELETE `/api/admin/public-cv/{handle}`, POST `.../unlock` (204/404; 403 for users tested).
- `SecurityConfig`: one extra matcher `GET /api/public/cv/*`, `/api/cv/public/preview/*` before `authenticated()`.
- `PublicCvMaintenance` (`cv/publiccv/internal`): daily `0 45 3 * * *` `purgeHolds(now)`, logs a count only. `@EnableScheduling` is already on (`CrawlScheduler`).

## Decisions and surprises
- Added to the public `PublicCvService`: `checkHandle(sub, handle): HandleUnavailable?` (enum `INVALID, RESERVED, TAKEN, HELD`; null = available, own handle and own hold included) and implemented it in `DefaultPublicCvService`. The Contract had no method for the availability check.
- `WindowLimiter` already sweeps (at 10,000 keys) and has a test (`many invented keys...`); nothing added.
- `sections` with duplicates are de-duplicated (first position kept). `settings` is optional in PUT (defaults all off); required in preview.
- Client IP: first `X-Forwarded-For` value, trimmed, 1 to 45 chars, else socket address.
- Logging: one INFO line per page answer `status`, `ms`, `etag` (8 chars); nothing on owner endpoints.
- Spring Security's firewall answers 400 (not our 404) for paths with an encoded `%25` or similar; and Spring adds `Content-Disposition` to 404 answers for a handle with a dot (`A.B`). Both are independent of whether a handle exists, so no enumeration, but the 404s are not byte-identical for those two inputs. The uniform-404 test uses the plausible/implausible forms that reach the controller.
- Taking a released handle back is allowed by `checkHandle`, but `save` still applies the 30-day change rule (409 `handle_change_too_soon`); the test asserts availability only.
- Preview expiry in the API test sets `expiresAt` of the stored tickets by reflection (no clock bean exists in the Spring context).
- Every API test uses its own `X-Forwarded-For` address from a JVM-wide counter, because the limiters live in the shared context.
- Not tested at HTTP level: `PublicCvMaintenance` (one repository call, `purgeHolds` is covered by `PublicCvRepositoryTests`).
- No new configuration; `jobhunter.public-cv.*` in `application.yaml` stays as group 3 left it.

## Files
- Main: `cv/internal/{PublicCvController,PublicCvPageController,PublicCvAdminController}.kt`, `cv/publiccv/internal/PublicCvMaintenance.kt`, edited `cv/publiccv/PublicCvService.kt`, `cv/publiccv/internal/DefaultPublicCvService.kt`, `accounts/internal/SecurityConfig.kt`.
- Test: `backend/src/test/kotlin/net/s4v3/backend/PublicCvApiTests.kt`.
- Spec/tasks: `openspec/changes/public-cv-page/tasks.md` (4.1 ticked). Not committed.
