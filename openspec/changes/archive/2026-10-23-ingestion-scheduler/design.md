# Design

## Context

`AtsCrawl.run()` exists (change `source-ats`, backend 0.4.0). It is idempotent, locks per board in the database, and refuses a board within 15 minutes of its last run. The only trigger today is `StartupCrawl` (opt-in, once). The backend runs one replica with virtual threads. The backend is an OAuth2 resource server that maps the `roles` claim to `ROLE_*` authorities (`SecurityConfig`); its only REST controller is `ProfileController` in `web.internal`. The auth service has `Roles.ADMIN` and a claims customizer that writes `roles` from the user's authorities, but only dev seed data creates admins. Google users are created or linked in `GoogleAccountService`, after `GoogleOidcUserService` has checked `emailVerified`.

## Goals / Non-Goals

**Goals:**
- Run the crawl every hour by default, and let an administrator change or stop the schedule at runtime through the API, durably.
- Never overlap, never die from one failed run, never fire real requests in tests or local runs by accident.
- Give the project owner an ADMIN account in production without hand-editing the database.

**Non-Goals:**
- A "run now" trigger, run history, dashboards, alerting, a roles UI or API, an API documentation page.

## Decisions

### A tick that reads the stored schedule
A component `CrawlScheduler` in `sources.internal` has one method `@Scheduled(fixedDelay = 30s)` (via `@EnableScheduling` in a small config class). Each tick it reads the schedule (a cached read of at most a few seconds is allowed), and compares its `updated_at` with the one it computed from. If different, or at startup, it computes `nextFire = CronExpression.parse(cron).next(now in zone)`. If the schedule is enabled and `now >= nextFire`, it starts a crawl (guarded, below) and recomputes `nextFire` from `now`. Disabled means no next fire.

- *Why this over `@Scheduled(cron)` or `TaskScheduler.schedule`:* the cron and zone change at runtime, which an annotation cannot follow, and a rescheduling `ScheduledFuture` must be kept consistent across API calls and (later) replicas. The tick has one source of truth (the row), picks up a change within 30 seconds, and gets the no-catch-up behavior for free because `nextFire` is always computed from "now".
- *Cost:* one small SELECT every 30 seconds, and fire times accurate to about 30 seconds. Acceptable for an hourly crawl.
- *Alternative:* Quartz or JobRunr. Tables, a dependency and a UI for what is one row and a loop.
- *Alternative:* ShedLock. The per-board advisory lock and the 15-minute minimum interval in `AtsCrawl` already stop two replicas from crawling the same board twice; with several replicas each would fire at the hour, one would crawl and the others would see `ALREADY_RUNNING` or `TOO_SOON` per board. Revisit when a second kind of scheduled job needs a real lock.

### Stored schedule
Table `crawl_schedule` in changeset `008-crawl-schedule` (new file `005-crawl-schedule.sql`): `id` int primary key with `CHECK (id = 1)`, `enabled` boolean not null, `cron` text not null, `zone` text not null, `updated_at` timestamptz not null, `updated_by` text not null (the user's `sub`). With no row, the schedule is the settings' initial value (`jobhunter.sources.schedule.enabled|cron|zone`, defaults `false`, `0 0 * * * *`, `Europe/Zurich`) and the settings are validated at startup when they enable it. The first POST inserts the row, later ones update it (`INSERT ... ON CONFLICT (id) DO UPDATE`).

- *Alternative:* keep the schedule in memory and a ConfigMap. A POST could not persist anything without cluster access.

### API
`CrawlScheduleController` in `sources.internal` (like `ProfileController` in `web.internal`; the module owns its endpoint):

- `GET /api/admin/crawl-schedule` → `{enabled, cron, zone, nextFire?, updatedAt?}`.
- `POST /api/admin/crawl-schedule` with `{enabled, cron, zone}` → same shape; 400 with `{field, message}` for invalid input.
- Authorization: `@PreAuthorize("hasRole('ADMIN')")` with method security enabled, or a request matcher for `/api/admin/**` in `SecurityConfig` (accounts module). Prefer the matcher `requestMatchers("/api/admin/**").hasRole("ADMIN")` in the existing chain so all admin paths are covered in one place, and a test that asserts a new `/api/admin/` path is protected by default. 401 comes from the resource server, 403 from the matcher.
- Validation: `CronExpression.isValidExpression`, `ZoneId.of` (caught), non-null fields, and a minimum gap: the two next fire times after "now" must be at least the minimum interval (`jobhunter.sources.min-interval`, 15 minutes) apart. This only catches the immediate gap; irregular crons such as `0 0,5 * * * *` are caught by the same check on the first two fires and are accepted when later gaps are larger. It is a guard against mistakes, not a rate limiter; `AtsCrawl`'s minimum interval remains the real protection.
- `nextFire` in the answer is computed the same way as in the tick.

### Overlap guard, failure handling, summary
As before: an `AtomicBoolean` around the crawl in the tick (virtual-thread schedulers do not serialize executions), `catch (Exception)` logging `javaClass.simpleName` only, and start and end lines built from `CrawlResult` counts only. The crawl runs on the tick thread (a virtual thread when Boot's virtual threads are on, so a long crawl does not block other scheduled tasks; if the scheduler is single-threaded, the guard also makes later ticks return at once).

### Admin accounts in production (auth service)
New setting `app.admin-emails` (env `ADMIN_EMAILS`, comma separated), empty by default. In the Google sign-in path, after `emailVerified` was checked and the account resolved, if the Google email matches an entry (case-insensitive, trimmed) the principal's authorities include `ROLE_ADMIN` on top of the stored roles; the claims customizer already turns authorities into the `roles` claim. The grant is computed per sign-in and not stored, so removing an email takes effect with the next token and no migration or cleanup is needed. Local-password accounts are not matched: a local registration with an unverified email must not be able to claim admin.

- *Alternative:* store `ADMIN` in `user_role` for listed emails. Needs a cleanup path when an email leaves the list, and persistent admin rights would outlive the configuration.
- *Alternative:* a SQL one-liner by hand in production. Works once, is undocumented, and cannot be tested.
- *Known limit:* a Google-linked account that already existed gets `ADMIN` on its next sign-in; tokens issued earlier keep their old roles until they expire.

### Settings and configuration
Backend: `jobhunter.sources.schedule.enabled|cron|zone` (initial values, see above). The prod overlay sets `JOBHUNTER_SOURCES_SCHEDULE_ENABLED=true`, so a fresh prod database starts hourly; after a POST the row decides. Auth: `ADMIN_EMAILS`, supplied as an environment variable in the prod overlay (a ConfigMap value, since an email is not a secret but should stay out of git history if the user prefers: it can be set through the existing `app-config` patch style or a Secret; the apply task picks the Secret option for the real address).

### Module boundaries and database
`sources` gains a table (`crawl_schedule`, owned by `sources`) and an HTTP controller; it still depends on `postings` only. No change to `AtsCrawl`. The auth change is inside the auth build.

## Risks / Trade-offs

- [A wrong schedule posted by an admin hammers employers] → the minimum gap check and `AtsCrawl`'s per-board 15-minute interval bound the load; the POST is limited to ADMIN.
- [The admin list is wrong or an email is reused] → only verified Google emails count; an empty list grants nothing; a test pins the local-account case.
- [Admin rights are not revocable for already issued tokens until they expire] → access tokens are short-lived; documented.
- [Everyone must sign in once more after the auth image is replaced because the signing key is regenerated] → known from earlier auth releases; the deploy notes say so, and the auth deploy happens first so the backend change can be tested with an admin token.
- [The tick fires up to 30 seconds late] → irrelevant for an hourly crawl.
- [Daylight saving] → the cron runs in the Europe/Zurich zone; on the night a time does not exist or repeats, Spring's behavior applies and at worst one run is skipped or doubled, both harmless.
- [Failed runs go unnoticed] → the end log line is the only signal; alerting is out of scope.

## Migration Plan

Backend changeset `008-crawl-schedule` is additive. Deploy order: auth image first (with `ADMIN_EMAILS` set to the owner's Google email), then the backend image with `JOBHUNTER_SOURCES_SCHEDULE_ENABLED=true`. Check on s4v3.net: sign in again, GET shows the default hourly schedule, a POST with a cron one minute ahead produces a crawl, and a POST restoring the hourly cron ends the check. Rollback: redeploy previous images; the table can stay, and without the new backend nothing reads it.

## Open Questions

- None that block the specs. Documenting all APIs (OpenAPI) is a separate change.
