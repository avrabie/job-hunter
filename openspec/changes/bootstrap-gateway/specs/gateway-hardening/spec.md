# Spec Delta

## Purpose

Defines the protective behaviour of the gateway: response security headers, timeouts, bounded connection reuse, and what it must not log.

## ADDED Requirements

### Requirement: Security response headers
The gateway SHALL add `X-Content-Type-Options: nosniff`, `X-Frame-Options`, `Referrer-Policy` and `Strict-Transport-Security` to every proxied response, and SHALL NOT add a `Content-Security-Policy` header.

#### Scenario: Headers present
- **WHEN** a proxied response is returned to the client
- **THEN** it carries the four headers

#### Scenario: Login page unaffected
- **WHEN** the generated login page of `auth` is requested through the gateway
- **THEN** no `Content-Security-Policy` header is added by the gateway, so the page's inline styles and scripts still work

#### Scenario: Downstream value kept
- **WHEN** a downstream response already sets one of these headers
- **THEN** the client receives a single value for it

### Requirement: Timeouts
The gateway SHALL apply a connect timeout of 5 seconds and a response timeout of 60 seconds to every route, configurable by property.

#### Scenario: Connect timeout
- **WHEN** a downstream address does not accept a connection within 5 seconds
- **THEN** the client receives a gateway error and the gateway stays healthy

#### Scenario: Overridable
- **WHEN** a timeout property is set in the environment
- **THEN** the new value is used

### Requirement: Bounded connection reuse
The gateway SHALL close idle downstream connections after 15 seconds and any connection after 60 seconds, so that connections to replaced pods do not cause errors after a deploy.

#### Scenario: Pool settings bound
- **WHEN** the application starts
- **THEN** the configured idle time, maximum lifetime and eviction interval are the values applied to the HTTP client pool

#### Scenario: After a downstream restart
- **WHEN** a downstream service is replaced by a new instance at the same address
- **THEN** a request made within 60 seconds either succeeds or gets a single 502, and later requests succeed

### Requirement: Nothing sensitive in logs
The gateway SHALL NOT log request or response bodies, `Authorization` headers, cookies or query strings at its default log level, and SHALL log to standard output.

#### Scenario: Request with token
- **WHEN** a request with an `Authorization` header and a cookie passes through at the default log level
- **THEN** no log line contains the token, the cookie value or the query string

### Requirement: No state
The gateway SHALL keep no session, cache or persistent store, and SHALL NOT set cookies of its own.

#### Scenario: No cookie of its own
- **WHEN** a request is proxied
- **THEN** the response contains only cookies set by the downstream service
