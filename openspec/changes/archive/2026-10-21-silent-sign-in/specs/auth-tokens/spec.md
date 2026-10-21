# Spec Delta

## ADDED Requirements

### Requirement: Silent sign-in check
For an authorization request that carries `prompt=none`, names the registered `spa` client and a redirect URI registered for it, the server SHALL NOT show a sign-in form. When the browser has a valid session it SHALL answer as for a normal request (redirect with a code). When it has none it SHALL redirect to that redirect URI with `error=login_required` and the request's `state`, and no code.

#### Scenario: Valid session
- **WHEN** a signed-in browser sends a valid authorization request with `prompt=none`
- **THEN** it is redirected to the redirect URI with a `code` and the same `state`

#### Scenario: No session
- **WHEN** a browser without a session sends a valid authorization request with `prompt=none`
- **THEN** it is redirected to the registered redirect URI with `error=login_required` and the same `state`, the response is not the login form, and no code is issued

#### Scenario: Unregistered redirect URI
- **WHEN** a request with `prompt=none` uses a redirect URI that is not registered for the client
- **THEN** the server does not redirect to that URI and answers as it does without `prompt`

#### Scenario: Unknown client
- **WHEN** a request with `prompt=none` names a client that is not registered
- **THEN** the server does not redirect to the given URI

#### Scenario: Other prompt values
- **WHEN** a request has no `prompt`, or `prompt=login`, and no session
- **THEN** the browser is sent to the sign-in form as before

#### Scenario: No identity in the answer
- **WHEN** the server answers `login_required`
- **THEN** the redirect contains only `error` and `state`, and nothing about any user
