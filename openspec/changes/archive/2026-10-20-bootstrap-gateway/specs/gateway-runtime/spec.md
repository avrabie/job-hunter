# Spec Delta

## Purpose

Defines how the gateway is configured, probed and run, so it can be started identically on a laptop and in the cluster.

## ADDED Requirements

### Requirement: Health endpoints
The gateway SHALL expose liveness and readiness endpoints without authentication at `/actuator/health/liveness` and `/actuator/health/readiness`; both SHALL succeed whenever the gateway itself is running, whether or not `backend` or `auth` are reachable, and no other actuator endpoint SHALL be exposed.

#### Scenario: Downstream down
- **WHEN** `backend` and `auth` are both unreachable
- **THEN** liveness and readiness still respond 200

#### Scenario: Other actuator endpoints
- **WHEN** a client requests `/actuator/env` or `/actuator/gateway/routes`
- **THEN** the response is 404

#### Scenario: Not routed through the public paths
- **WHEN** a client requests `/api/actuator/health` through the gateway
- **THEN** the request is forwarded to `backend` as an ordinary API path, not answered by the gateway's own actuator

### Requirement: Configuration from environment
The gateway SHALL read the `backend` and `auth` base addresses from `MICROSERVICES_BACKEND_URI` and `MICROSERVICES_AUTH_URI`, with local defaults `http://localhost:8081` and `http://localhost:9090`, and the timeouts and pool settings from properties, and SHALL contain no credentials.

#### Scenario: Override
- **WHEN** `MICROSERVICES_BACKEND_URI` is set to another address
- **THEN** requests to `/api/` go to that address

#### Scenario: Defaults
- **WHEN** no variable is set
- **THEN** `/api/` goes to `http://localhost:8081` and `/auth/` to `http://localhost:9090`

### Requirement: Setting names verified at build time
The test suite SHALL fail if a timeout or pool setting is misspelled or placed under a configuration prefix the gateway version ignores.

#### Scenario: Ignored prefix
- **WHEN** a setting moves to a prefix that the gateway does not bind
- **THEN** a test fails because the effective value differs from the intended one

### Requirement: Listening port
The gateway SHALL listen on port 8080 by default, configurable by `SERVER_PORT`.

#### Scenario: Default port
- **WHEN** it starts with no port setting
- **THEN** it accepts connections on 8080

### Requirement: Local full-stack run
`gateway/README.md` SHALL document how to run `gateway`, `backend` and `auth` together locally so that `http://localhost:8080/auth/.well-known/openid-configuration` and `http://localhost:8080/api/` are reachable through the gateway.

#### Scenario: Local routes
- **WHEN** a developer follows the README with all three services running
- **THEN** the discovery document is returned for `/auth/...` and `/api/...` returns 401 without a token

### Requirement: Tests without external services
The gateway's tests SHALL run without network access beyond localhost, using stub downstream servers started by the tests.

#### Scenario: Offline test run
- **WHEN** the test suite runs with no `backend` or `auth` running
- **THEN** it passes
