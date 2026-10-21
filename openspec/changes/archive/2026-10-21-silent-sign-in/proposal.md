# Proposal

## Why

After a reload, a new tab or a lapsed session, the UI starts a sign-in redirect on its own (the "session survives a reload" behaviour). When the session at `auth` has ended, the user lands on the `auth` sign-in form without ever seeing the landing page, and a signed-out visitor who opens `/profile` is sent to the form at once. Both feel like being pushed. The cause is that `auth` ignores `prompt=none`: it shows the login form instead of answering "not signed in", so the UI cannot check quietly first (found in `bootstrap-ui` task 2.1).

## What Changes

- `auth` honours `prompt=none` on the authorization endpoint for the registered `spa` client: with a valid session it answers as today (a code); without one it redirects back to the registered redirect URI with `error=login_required` and the caller's `state`, and shows no form.
- The UI uses that check for the reload restore: with the "signed in before" flag set and no tokens it asks `auth` quietly; on `login_required` it clears the flag and shows the landing page for the page that was open.
- A signed-out visitor who opens `/profile` sees the landing page (with "Sign in" and "Register") and returns to `/profile` after signing in; nothing redirects on its own.
- The UI remembers which of its own pages to return to after sign-in, from a fixed list (already built in `bootstrap-profile`; this change specifies it).
- Deploy `auth` and `ui` to `s4v3.net`.

**Explicitly out of scope:** a persistent signing key (restarting `auth` still invalidates all tokens, so this deploy signs everyone out once); a hidden-iframe silent flow (the secure-headers filter denies framing); `prompt=login`, `prompt=consent` or `max_age`; any change to the token or refresh behaviour; Google sign-in; registration abuse protection.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `auth-tokens`: new requirement for the silent check (`prompt=none`).
- `ui-sign-in`: the reload requirement now ends on the landing page when the session has ended; new requirement for the page to return to after sign-in.

## Impact

- **Modules touched:** `auth` (authorization-server security chain: an entry point and its matcher; no change to token generation, users or the database) and `ui` (`AuthProvider`, `CallbackPage`, `Landing`, `ProfilePage`). `backend`, `gateway` and `k8s` manifests are untouched except image tags.
- **Dependencies:** none new.
- **Personal data:** the redirect carries only `error` and `state`; no identity or token. Nothing new is stored or logged. The `state` value is echoed back to the redirect URI it came from, and only to a URI that is registered for the client.
- **Per-user scoping:** unchanged.
- **Claude cost:** none.
- **Security note:** the redirect target must be validated against the registered client before anything is sent there (an unvalidated `redirect_uri` would be an open redirect). Anything that does not validate falls back to today's behaviour.
- **Operational note:** replacing the `auth` Pod regenerates the signing key, so every user must sign in again once after the deploy.
