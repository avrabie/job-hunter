# Tasks

## 1. auth: silent check

- [x] 1.1 Add the entry point and matcher for `prompt=none` to the authorization-server chain in `auth`, placed before the login entry point; redirect only to a registered `redirect_uri` of a registered client, with `error=login_required` and the encoded `state`.
- [x] 1.2 Tests with a real authorization request: valid session gets a code and the same state; no session gets `login_required` and the same state and no login form; `prompt=login` and no `prompt` still go to `/login`; unregistered redirect URI, unknown client, missing `redirect_uri`, a differently-cased or encoded variant of a registered URI, and `state` with special characters each do not redirect to the given URI (or echo it correctly); the redirect carries only `error` and `state`.
- [x] 1.3 Full `auth` test suite green.

## 2. UI: quiet restore and landing

- [x] 2.1 `AuthProvider` restore uses `prompt: 'none'`; `completeSignIn` resolves `{ signedIn, returnTo }`, treating `login_required` as signed out (clears the flag); `CallbackPage` navigates to `returnTo` either way; any other error still shows the fixed failure page.
- [x] 2.2 `ProfilePage` shows the landing page when signed out instead of starting a sign-in; the landing page takes the page to return to.
- [x] 2.3 Tests: restore asks with `prompt=none` and the return path; `login_required` ends on the landing page with the flag cleared and no second redirect; another error shows "Sign-in failed"; signed-out `/profile` shows the landing page and makes no sign-in call until "Sign in" is chosen, which returns to `/profile`; foreign return values go home.
- [x] 2.4 Lint, typecheck, build and the image test pass.

## 3. Local run

- [x] 3.1 Dropped by the user's decision: no local browser run; the same checks (sign in and reload, lapsed session, signed-out `/profile`, clean console) are done on `https://s4v3.net` in 4.2.

## 4. Deploy and verify

- [x] 4.1 With the user's go-ahead: build and push `moldovean/job-hunter-auth:0.2.0` and `moldovean/job-hunter-ui:0.3.0` (amd64), set the prod tags, apply to `s4v3`, wait for the rollouts, and verify an authorization request with `prompt=none` and no session returns `login_required` at `https://s4v3.net/auth`. Note in `k8s/README.md` that everyone signs in once more after the `auth` restart.
- [x] 4.2 The user checks `https://s4v3.net` in a browser (landing page first after a lapsed session; `/profile` signed out shows the landing page) and the result is recorded.

## 5. Wrap-up

- [x] 5.1 Archive with `/opsx:archive silent-sign-in` after 4.2.
