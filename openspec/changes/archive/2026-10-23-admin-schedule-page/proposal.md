# Proposal

## Why

The crawl schedule can be read and changed only through `/api/admin/crawl-schedule`, which needs a bearer access token. A browser address bar sends none (the SPA keeps its token in memory), so the administrator, who can now sign in with Google, has no practical way to use the API. A page in the UI can use the SPA's own token.

## What Changes

- Add an `/admin` page that shows the crawl schedule (enabled, cron, time zone, next run, last change) and lets an administrator change it.
- Add an "Admin" button to the signed-in shell, shown only when the access token carries the `ADMIN` role. This only decides what to display; the backend still enforces the role.
- Show a clear "not allowed" message when the API answers 403, and the existing sign-in handling when it answers 401.
- Show the server's validation message (field and text) when a change is rejected with 400.
- No backend change. The result of the last crawl run is not offered by the API and is out of scope.

## Capabilities

### New Capabilities
- `ui-admin`: the administrator page of the web UI: who sees it, what it shows and how the schedule is changed.

### Modified Capabilities

## Impact

- UI only: new `ui/src/admin/` module, a route in `App.tsx`, a button in `pages/Shell.tsx`, styles, UI README.
- Reads the `roles` claim of the access token in the browser, for display decisions only.
- Deploy: UI image only (`0.6.0`); backend and auth are not touched, so nobody is signed out.
