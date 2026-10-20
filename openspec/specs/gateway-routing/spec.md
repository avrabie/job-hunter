# gateway-routing Specification

## Purpose
Defines how the gateway routes requests from the edge to `backend` and `auth`, and what it passes through unchanged.

## Requirements

### Requirement: Path routing
The gateway SHALL route requests whose path starts with `/api/` to `backend` and requests whose path starts with `/auth/` to `auth`, and SHALL keep the request path unchanged.

#### Scenario: API route
- **WHEN** a client requests `GET /api/ping`
- **THEN** `backend` receives `GET /api/ping`

#### Scenario: Auth route
- **WHEN** a client requests `GET /auth/.well-known/openid-configuration`
- **THEN** `auth` receives `GET /auth/.well-known/openid-configuration`

#### Scenario: Query and method preserved
- **WHEN** a client sends `POST /auth/oauth2/token?x=1` with a form body
- **THEN** `auth` receives the same method, query string and body

### Requirement: Unknown paths
The gateway SHALL answer 404 for any path outside `/api/` and `/auth/`, and SHALL NOT forward it to any service.

#### Scenario: Unrouted path
- **WHEN** a client requests `GET /internal/secrets`
- **THEN** the gateway responds 404 and no downstream service receives a request

#### Scenario: Prefix lookalike
- **WHEN** a client requests `GET /apix/foo` or `GET /authority`
- **THEN** the gateway responds 404

### Requirement: Pass-through without authorization
The gateway SHALL forward the `Authorization` header, cookies and bodies unchanged, SHALL NOT validate or modify tokens, and SHALL NOT reject a request for lacking a token; the downstream service decides.

#### Scenario: Token forwarded
- **WHEN** a client calls `/api/x` with `Authorization: Bearer abc`
- **THEN** `backend` receives exactly `Bearer abc`

#### Scenario: No token
- **WHEN** a client calls `/api/x` without a token and `backend` answers 401
- **THEN** the client receives that 401 and its headers unchanged

#### Scenario: Gateway adds no identity
- **WHEN** any request is forwarded
- **THEN** the gateway adds no header that identifies a user or a role

### Requirement: Responses relayed unchanged
The gateway SHALL relay downstream status codes, bodies and `Set-Cookie` and `Location` headers unchanged, apart from the headers listed in `gateway-hardening`.

#### Scenario: Redirect from auth
- **WHEN** `auth` answers 302 with a `Location` header
- **THEN** the client receives the same status and `Location`

### Requirement: Original host and scheme reach the services
The gateway SHALL deliver to the downstream service the original public host and scheme as sent by the edge in `X-Forwarded-Host` and `X-Forwarded-Proto`, as the first or only value, and SHALL NOT replace them with its own address.

#### Scenario: Edge headers
- **WHEN** the edge sends `X-Forwarded-Host: s4v3.net` and `X-Forwarded-Proto: https` with a request to `/auth/login`
- **THEN** `auth` sees host `s4v3.net` and scheme `https` as the first values of those headers

#### Scenario: Direct call
- **WHEN** a request arrives without forwarded headers
- **THEN** the downstream service receives the gateway's view of the original host and scheme

### Requirement: Downstream failure handling
The gateway SHALL answer 502 when a downstream service refuses the connection or closes it early, and 504 when it does not respond within the response timeout, without leaking internal addresses in the body.

#### Scenario: Service down
- **WHEN** `backend` is not running and a client calls `/api/x`
- **THEN** the response is 502 and the body does not contain the downstream address

#### Scenario: Slow service
- **WHEN** `backend` does not answer within the response timeout
- **THEN** the response is 504

#### Scenario: Recovery
- **WHEN** a downstream service is restarted
- **THEN** requests through the gateway succeed again without restarting the gateway
