# edge-routing Specification

## Purpose
Defines the public entry point of the application: hostname, TLS, HTTP redirect, and the path routing from the edge to the UI and, through the Spring gateway, to the auth service and the backend.

## Requirements

### Requirement: Single public hostname
The system SHALL expose the UI, the `/auth` paths and the `/api` paths on one hostname, so the browser sees one origin and no CORS configuration is needed.

#### Scenario: One origin
- **WHEN** the SPA loads from the public hostname and calls `/api/...` and `/auth/...`
- **THEN** both requests go to the same scheme, host and port as the page

### Requirement: Path routing
The edge SHALL send requests for `/` to `ui`, requests for `/api` and `/auth` to the Spring gateway, and requests for `/cv/<handle>` to the Spring gateway with the path rewritten to `/api/public/cv/<handle>`; only the Spring gateway SHALL route onward to `backend` and `auth`.

#### Scenario: UI route
- **WHEN** a client requests `/` or an unknown non-API path
- **THEN** the response comes from `ui`

#### Scenario: API route
- **WHEN** a client requests a path under `/api/`
- **THEN** the request reaches `backend` through the Spring gateway

#### Scenario: Auth route
- **WHEN** a client requests `/auth/.well-known/openid-configuration`
- **THEN** the request reaches `auth` through the Spring gateway and the response is its discovery document

#### Scenario: No direct service access
- **WHEN** a client requests a path that only exists inside `backend` or `auth` and is not under `/api` or `/auth`
- **THEN** the edge does not reach those services

#### Scenario: Public CV address
- **WHEN** a client requests `/cv/ada-example`
- **THEN** the request reaches `backend` as `/api/public/cv/ada-example` through the Spring gateway and the response is the backend's page with its own security headers, not the UI's

#### Scenario: Only the handle is rewritten
- **WHEN** a client requests `/cv/` with nothing after it, or `/cv/a/b`
- **THEN** the answer is a 4xx with no content (the backend does not serve it) and no other backend path is reachable through `/cv/`

### Requirement: TLS termination
In production the edge SHALL serve HTTPS with a certificate issued for the public hostname and renewed automatically.

#### Scenario: Valid certificate
- **WHEN** a client opens the public hostname over HTTPS
- **THEN** the certificate is valid for that hostname and issued by the configured issuer

#### Scenario: Renewal
- **WHEN** the certificate approaches its renewal window
- **THEN** cert-manager renews it without an operator action

### Requirement: HTTP redirects to HTTPS
In production the edge SHALL answer plain HTTP requests with a permanent redirect to the same path on HTTPS.

#### Scenario: Redirect
- **WHEN** a client requests `http://<host>/api/x`
- **THEN** the response is a redirect to `https://<host>/api/x`

### Requirement: Forwarding headers reach the auth service
The edge and the Spring gateway SHALL pass the original host and scheme to `auth`, so the issuer it advertises is the public URL.

#### Scenario: Issuer is public
- **WHEN** the discovery document is fetched through the edge
- **THEN** its `issuer` is `https://<public host>/auth`, not an in-cluster address

### Requirement: Token validation reachable from the backend
`backend` SHALL be able to fetch the issuer's discovery document and signing keys from inside the cluster using the configured issuer URI, which is the public URL.

#### Scenario: Valid token accepted
- **WHEN** a client obtains a token from `auth` through the edge and calls `/api/` with it
- **THEN** `backend` validates the token and processes the request

#### Scenario: Issuer unreachable
- **WHEN** `auth` is down while a request with a token reaches `backend`
- **THEN** `backend` stays running, the request is rejected, and requests succeed again once `auth` is back
