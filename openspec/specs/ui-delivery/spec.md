# ui-delivery Specification

## Purpose

Defines how the web UI is built, tested, run on a developer machine, packaged and served, so that it is reproducible and safe to expose at the public hostname.

## Requirements

### Requirement: Independent build
The `ui/` directory SHALL build, lint and test on its own with a lockfile, without the other builds, and SHALL fail the build on lint, type or test errors.

#### Scenario: Clean checkout
- **WHEN** the documented install, lint, test and build commands run in `ui/`
- **THEN** all succeed and the build output is a directory of static files

### Requirement: Configuration at runtime
The image SHALL contain no environment-specific URL: the UI SHALL derive the issuer, redirect and post-logout addresses from the origin the page was loaded from.

#### Scenario: Same image everywhere
- **WHEN** the same image is served at `https://s4v3.net` and at `http://localhost:8080`
- **THEN** sign-in uses `<origin>/auth` as issuer and `<origin>/callback` as redirect address in each

### Requirement: Local development against the local stack
The README SHALL describe running the dev server with `/api` and `/auth` proxied to the local gateway on port 8080, so the browser has one origin.

#### Scenario: Dev sign-in
- **WHEN** the dev server, gateway, `auth` and `backend` run locally
- **THEN** a user can register, sign in and see the shell at the dev server address

### Requirement: Static serving with fallback
The served image SHALL return `index.html` for any path that is not a file and does not start with `/api/` or `/auth/`, SHALL cache fingerprinted assets for a long time, and SHALL NOT cache `index.html`.

#### Scenario: Deep link
- **WHEN** a browser requests `/callback?code=x`
- **THEN** the response is `index.html` with status 200

#### Scenario: Missing asset
- **WHEN** a browser requests `/assets/does-not-exist.js`
- **THEN** the response is 404, not `index.html`

#### Scenario: Cache headers
- **WHEN** `index.html` and a fingerprinted asset are requested
- **THEN** `index.html` is sent with `no-cache` and the asset with a long `max-age`

### Requirement: Security headers
The served UI SHALL send a Content-Security-Policy that allows scripts, styles and connections only from its own origin, plus `X-Content-Type-Options: nosniff`, a strict referrer policy, and a policy that forbids framing.

#### Scenario: Headers present
- **WHEN** `/` is requested
- **THEN** the response carries all four headers

#### Scenario: Inline script blocked
- **WHEN** the page is loaded in a browser
- **THEN** the UI works with the policy enforced and the console shows no policy violations

### Requirement: Hardened container
The image SHALL run as a non-root user with a read-only root filesystem and listen on an unprivileged port.

#### Scenario: Restricted run
- **WHEN** the image runs with a read-only root filesystem and all capabilities dropped
- **THEN** it starts and serves `/`

### Requirement: Deployed behind the edge
The production deployment SHALL serve the UI at `/` of the public hostname, and a sign-in there SHALL succeed through the edge, gateway and auth service.

#### Scenario: Live sign-in
- **WHEN** a user registers and signs in at `https://s4v3.net`
- **THEN** the signed-in shell appears with their identity and the `/api/` call shows the backend's 404 message
