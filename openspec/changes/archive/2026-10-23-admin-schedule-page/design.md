# Design

## Context

The schedule API (`GET`/`POST /api/admin/crawl-schedule`) already exists and returns `{enabled, cron, zone, nextFire, updatedAt}`; a change is validated server-side (cron, zone, minimum interval) and answers 400 with `{error, field, message}`. The SPA keeps tokens in memory and calls the API through `apiFetch`. The access token carries a `roles` claim (names without `ROLE_`), which the ID token does not.

## Goals / Non-Goals

**Goals:**
- An administrator can read and change the schedule from the browser with no pasted token.
- A non-administrator never sees the entry point and gets a clear message if they open the URL.

**Non-Goals:**
- Showing the outcome of the last crawl run: the API has no such field. A later change can add it.
- Triggering a crawl on demand, or editing boards.
- Making the UI a security boundary.

## Decisions

- **Role detection in the browser.** Read `roles` from the access token payload (base64url JSON, no signature check) to decide whether to show the Admin button. Alternative: always show the button and rely on 403; rejected because ordinary users would see a dead link. The decode is display-only; a forged claim would only reveal a page whose API calls still fail with 403.
- **The page also handles 403.** Opening `/admin` directly is always possible, so the page treats 403 as "administrators only" regardless of the token check.
- **Same structure as postings.** An `adminApi.ts` with fixed result kinds (`loaded`, `unauthorized`, `forbidden`, `invalid`, `failed`) that never carry a response body, and an `AdminPage.tsx`; one route in `App.tsx`.
- **Validation messages.** The 400 body holds only `field` and a fixed server text, no user input echo, so it is shown as text. Anything else maps to the existing fixed messages.
- **Cron is edited as text.** No cron builder; the page shows the format hint "Spring cron with seconds, for example `0 0 * * * *`" and the minimum interval rule is left to the server.

## Risks / Trade-offs

- Role check in the client is advisory; mitigated by backend enforcement and the 403 handling.
- The access token can lose the role (role removed) before it expires; the next API call returns 403 and the page shows the message.
- Without a last-run field the admin cannot see whether the last crawl succeeded; they use the pod logs until a follow-up adds it.
