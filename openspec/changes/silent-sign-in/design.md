# Design

## Context

`auth` is Spring Authorization Server with one `spa` client. In `SecurityConfig.authorizationServerChain` an unauthenticated browser request to the authorization endpoint is sent to `/login` by `LoginUrlAuthenticationEntryPoint` (for `text/html`). The login redirect happens in the exception translation filter, before the authorization endpoint looks at `prompt`, so `prompt=none` has no effect (measured on 2026-10-21, `bootstrap-ui` task 2.1). The UI restores a session after a reload by starting a normal sign-in redirect when a `jh.signedIn` flag is set.

## Decisions

### `auth`: an entry point for `prompt=none`, in front of the login redirect
Register a second `AuthenticationEntryPoint` in the same `exceptionHandling` with `defaultAuthenticationEntryPointFor(promptNoneEntryPoint, promptNoneMatcher)` **before** the login one; the delegating entry point uses the first matcher that matches. The matcher is true only when all of these hold: the path is the authorization endpoint (`/oauth2/authorize`), `prompt` is a space-separated list containing `none`, `client_id` is a registered client, and `redirect_uri` is exactly one of that client's registered redirect URIs. The entry point then redirects to that URI with `error=login_required` and `state` (URL-encoded, only if present). Anything that fails the matcher falls through to the existing login redirect, so no unvalidated URI is ever redirected to. Registered-client and redirect checks use `RegisteredClientRepository`, the same source the endpoint uses, with exact string comparison like Spring does.

*Alternatives:* a servlet filter ahead of the chain (more code, duplicates the endpoint's parameter parsing); a Spring Authorization Server customisation of `OAuth2AuthorizationEndpointFilter` (it is not reached while unauthenticated); a hidden iframe (blocked by the framing deny on `/auth`, and third-party cookie rules make it unreliable).

Only `response_mode=query` (the default) is handled; the UI uses it. `iss` is not added to the error response (the UI does not need it; code responses do not carry it either).

### UI: ask quietly, then show the landing page
`AuthProvider` restore: with the flag set and no user, call `signinRedirect({ prompt: 'none', state: { returnTo } })`. On `/callback`, `signinCallback` rejects with an `ErrorResponse` whose `error` is `login_required`; `completeSignIn` turns that into a normal result `{ signedIn: false, returnTo }` instead of a failure, clears the flag, sets the state to signed out, and the callback page navigates to `returnTo`. Any other error keeps today's fixed "Sign-in failed" page. The explicit "Sign in" button keeps starting a normal redirect (no `prompt`), so the user sees the form when needed. Pages that need a user (`/profile`) render the landing page when signed out rather than starting a sign-in; the landing page's "Sign in" asks to return to that page.

### Return path
Unchanged from `bootstrap-profile`: `safeReturnTo` allows `/` and `/profile` only; the value travels in the oidc-client-ts state (`sessionStorage`, same entry as the PKCE state) and is validated again on the way back.

## Risks and trade-offs

- **Every reload still makes the round trip** (UI → `auth` → UI). It is a redirect, not a form, and it was already the case.
- **A mistake in the matcher is an open redirect.** Mitigation: exact match of `redirect_uri` against the registered set, tests for an unregistered URI, an unknown client, a missing `redirect_uri`, and encoded or case-varied variants, each of which must NOT redirect to the given URI.
- **`auth` restart signs everyone out** (signing key regenerated). Known and accepted; the deploy notes say so.
- **Browsers that block or partition cookies** cannot be told apart from "no session": the user sees the landing page and signs in normally. Acceptable.

## Migration plan

Build and test `auth` and `ui`; deploy `auth` first, then `ui`. Between the two, an old UI that still sends a plain redirect keeps working. Roll back by re-applying the previous tags.

## Open questions

- None. A persistent signing key stays a separate follow-up.
