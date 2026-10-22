# Proposal

## Why

The Greenhouse crawl works (40 Swiss IT postings were created on s4v3.net) but only runs when someone redeploys with a startup setting. Postings go stale and new ads are never found. A scheduler runs the crawl by itself, every hour, and an administrator can change the schedule at runtime through the API without a redeploy.

## What Changes

- Add a scheduled crawl in the `sources` module that calls `AtsCrawl.run()`. Default schedule: **every hour on the hour, Europe/Zurich** (`0 0 * * * *`).
- Store the schedule in the database (`crawl_schedule`: enabled, cron, zone, who and when) so a change survives restarts. The settings `jobhunter.sources.schedule.*` give the initial value; once a schedule is stored, the stored one wins. The schedule is **off by default**, so local runs and tests never contact real servers; the prod overlay sets the initial value to on.
- Add an admin API: `POST /api/admin/crawl-schedule` sets `enabled`, `cron` and `zone` and answers with the stored schedule and its next fire time; `GET /api/admin/crawl-schedule` returns the current one. Both need the `ADMIN` role. An invalid cron or zone, or a cron that fires more often than the minimum interval between runs of a board (15 minutes), is rejected with a 400 that names the field.
- A change takes effect within about 30 seconds without a restart: a 30-second tick reads the stored schedule and starts a crawl when the next fire time has passed.
- A run that fails or throws is logged (outcome and counts only) and does not stop later runs. A run still going when the next is due is skipped, not queued. A run missed because the pod was down is not made up.
- Each run logs one start line and one summary line (boards per outcome, job totals).
- Give an administrator in production: the auth service gets a setting listing admin emails; a user who signed in with Google (verified email) and is on that list gets the `ADMIN` role in their access token. Local accounts never get `ADMIN` this way.

### Why every hour

A run where nothing changed costs about ten cheap list requests (HTTP 304 from the stored `ETag`), and about a minute when a board changed. That is light enough for 24 runs a day, and new ads are visible within an hour. The 15-minute minimum interval per board still protects the employers' servers from a mistaken schedule, and the API refuses schedules faster than that.

### Observed about the current code

- `AtsCrawl.run()` is safe to call at any time: a database lock per board stops overlapping runs (also across replicas), and a 15-minute minimum interval per board turns a second run too soon into `TOO_SOON` without a request. So no extra distributed lock (ShedLock) is needed.
- With virtual threads, Spring's scheduler can start a task while the previous one runs, so an in-process overlap guard is needed.
- The `ADMIN` role exists in the auth service (`Roles.ADMIN`) and the backend maps the `roles` claim to `ROLE_*` authorities, but nothing grants `ADMIN` in production: only the dev seed data does. Without a way to grant it, an admin endpoint could not be used on s4v3.net.
- The gateway already routes `/api/**` to the backend, so the new endpoints need no gateway change.
- Only Google sign-in proves ownership of an email (the auth service refuses Google accounts whose email Google has not verified); that is why only Google sign-ins can match the admin list.

### Out of scope

- A "run now" endpoint or any other trigger, an admin UI, a page that documents all APIs (OpenAPI is planned separately).
- Expiring or closing postings that leave a board.
- New sources (Lever and others).
- A table of past runs or a dashboard: runs are visible in the logs only.
- Alerting on failed runs.
- Managing roles in general (a UI or API to grant roles); only the admin email list is added.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `sources`: the scheduled crawl, the stored schedule and the admin API for it.
- `auth-tokens`: access tokens carry the `ADMIN` role for configured admin emails of verified Google sign-ins.

## Impact

- **Modules:** `sources` (scheduler, stored schedule, admin controller). `auth` (admin email setting and role grant). No new dependency: Spring's scheduling and security are used.
- **Database:** backend changeset `008-crawl-schedule` (one table, one row). No auth schema change.
- **Deployment:** new backend and auth images; `ADMIN_EMAILS` for auth and `JOBHUNTER_SOURCES_SCHEDULE_ENABLED=true` for the backend in the prod overlay. Replacing the `auth` Pod regenerates the signing key, so everyone signs in once more.
- **Personal data:** the schedule row keeps the changing user's `sub` (an id, no email or name). Logs hold the new cron and `enabled` only. The admin email list is configuration in the cluster, not in git.
- **Per-user scoping:** the schedule is global, like postings, and is writable by `ADMIN` only; a scenario proves a normal user gets 403. **Claude cost:** none.
