## MODIFIED Requirements

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
- **THEN** the answer is a 404 from `backend` and no other backend path is reachable through `/cv/`
