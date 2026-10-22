# Tasks

## 1. Schema, settings and schedule logic (backend)

- [x] 1.1 Add `backend/src/main/resources/db/changelog/005-crawl-schedule.sql` with changeset `008-crawl-schedule` (`crawl_schedule`: `id` int primary key with `CHECK (id = 1)`, `enabled`, `cron`, `zone`, `updated_at`, `updated_by`) with `--rollback`, include it in `db.changelog-master.yaml`, and raise the changeset count in `DatabaseBaselineTests` to 8 with a check for the new table. Verify with a schema test (a second row is rejected, null `cron` is rejected) and that `DatabaseBaselineTests` passes, including the "running again changes nothing" test.
- [x] 1.2 Add the nested `Schedule` settings (`enabled`, `cron`, `zone`) to `SourcesSettings` with the defaults `false`, `0 0 * * * *`, `Europe/Zurich`, and the startup validation of enabled initial settings (errors name the setting). Verify with unit tests: the defaults, the next three fire times of the default cron after a fixed instant are the next three full hours in Europe/Zurich, `not a cron` and `Mars/Base` fail with the setting named when enabled, and pass when disabled.
- [x] 1.3 Implement the schedule store over `crawl_schedule` (read with fallback to the settings, upsert with `updated_by` and `updated_at`) and the validation (cron, zone, required fields, minimum gap using `min-interval`). Verify with integration tests: no row gives the settings' values; a stored row wins; upsert twice leaves one row; and unit tests for every validation scenario (bad cron, bad zone, every minute rejected, hourly and `0 30 6,14 * * *` accepted, missing field).

## 2. Scheduler

- [x] 2.1 Implement `CrawlScheduler` (30-second tick, change detection by `updated_at`, `nextFire` computed from now, `AtomicBoolean` overlap guard, exception-type-only failure log, start and end summary lines) with its scheduling configuration. Verify with unit tests that drive the tick with a fake clock and a fake `AtsCrawl`: no fire before `nextFire`, one fire at it, a firing while a blocked crawl runs is skipped and logged, a later firing runs again, a throwing crawl is logged by type only and the next fire still happens, a disabled schedule never fires, and after a schedule change or a start after the due time no catch-up run happens and the next fire time follows the new schedule.
- [x] 2.2 Verify the summary line with a unit test: 9 completed and 1 failed board give the board counts per outcome and the summed job counts, and a marker placed in a board's `reason` never appears in the line.
- [x] 2.3 Verify the wiring with integration tests: with default settings no request is made and `GET` reports disabled; with a schedule row written straight into the table (the API would refuse such a fast cron) as `0/20 * * * * *`, a local fake Greenhouse server sees a crawl request within about a minute.

## 3. Admin API (backend)

- [x] 3.1 Implement `CrawlScheduleController` (`GET` and `POST /api/admin/crawl-schedule`, 400 body with field and message) and the `/api/admin/**` rule requiring `ADMIN` in the existing security chain. Verify with `@ApiTest` tests using the `MockIssuer`: no token → 401 and nothing changes; a token with only `USER` → 403 on GET and POST and the stored schedule is unchanged; an `ADMIN` token → 200 on GET with the default hourly schedule; POST of a valid schedule → 200 with the stored schedule and a `nextFire`, a later GET answers the same, and the row's `updated_by` is the token's `sub`; POST `enabled:false` → no `nextFire`; the validation 400s name `cron`, `zone` and the missing field.
- [x] 3.2 Verify the change takes effect without restart and survives one: a test posts a schedule and asserts the scheduler's next fire time reflects it on the next tick; a test builds a new scheduler instance over the same database and asserts it uses the stored schedule over differing settings.
- [x] 3.3 Add a log-capture test, in the style of `PostingLoggingTests`, asserting that changing the schedule logs `enabled` and `cron` and never the administrator's `sub`, email or name.
- [x] 3.4 Verify `ModularityTests` passes and that a new path under `/api/admin/` that no test mentions is still refused to a `USER` token (a test endpoint in test sources).

## 4. Admin accounts (auth service)

- [x] 4.1 Add the setting `app.admin-emails` (env `ADMIN_EMAILS`, default empty) to the auth service and grant `ROLE_ADMIN` on Google sign-in for a verified email on the list, compared case-insensitively and trimmed, not stored. Verify with tests in the auth build for every scenario in the `auth-tokens` delta: listed Google user → `roles` has `USER` and `ADMIN`; case-insensitive match; unlisted user → `USER` only; a local-password account with a listed email → no `ADMIN`; empty list → none; removing the email → the next token has no `ADMIN`.
- [x] 4.2 Run the full auth build (`./gradlew build` in `auth/`) and verify it passes.

## 5. Docs and integration

- [x] 5.1 Document in `backend/README.md` the three schedule settings, the default hourly schedule and why, the admin endpoints with curl examples (an `ADMIN` access token is needed; describe how to obtain one through the existing sign-in flow), the 400 rules, the no-overlap and no-catch-up behavior; document `ADMIN_EMAILS` in the auth README and `k8s/README.md` (environment variables section); update the `sources` row in `PLAN.md`. Verify the setting names in the docs match the code.
- [x] 5.2 Run the full backend build (`./gradlew build` in `backend/`) and verify it passes.

## Workflow follow-up

- Deploy auth first: build and push the auth image, set `ADMIN_EMAILS` for the prod `auth` Deployment (a Secret value for the real address), bump the tag, preview with `kubectl --context s4v3 diff -k k8s/overlays/prod`, apply; tell the user everyone signs in once more.
- Deploy the backend: add `JOBHUNTER_SOURCES_SCHEDULE_ENABLED=true` to the prod overlay as a patch with a comment, build and push, bump the tag, diff, apply.
- With the user's go-ahead check on s4v3.net: the user signs in again and GETs the schedule (shows hourly), POSTs a cron one minute ahead, the log shows a start and end line, then POSTs the hourly cron back. Note both deploys in `k8s/README.md`.
- Archive the change (sync `sources` and `auth-tokens`), then decide the next change (matching prefilter, posting expiry, or an OpenAPI page).
