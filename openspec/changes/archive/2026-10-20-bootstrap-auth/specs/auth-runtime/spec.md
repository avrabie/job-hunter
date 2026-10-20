# Spec Delta

## Purpose

Defines the auth service's runtime baseline: database schema and migrations, health endpoints, configuration from the environment, and reproducible local and test setup.

## ADDED Requirements

### Requirement: Own database and versioned schema
The service SHALL use its own PostgreSQL database `authdb` and SHALL create and change its schema only through ordered, versioned Liquibase changesets that run at startup and stop the service from starting when one fails.

#### Scenario: Empty database
- **WHEN** the service starts against an empty `authdb`
- **THEN** all changesets apply and the service becomes ready

#### Scenario: Up to date
- **WHEN** it starts against a database that already has all changesets
- **THEN** none is re-applied and the data is unchanged

#### Scenario: Failing changeset
- **WHEN** a changeset fails
- **THEN** the service does not start and the error names the changeset

### Requirement: Identity data model
The schema SHALL store each user with a UUID primary key, a unique case-insensitive email, a password hash, optional first and last names, enabled flag, provider (`LOCAL` or `GOOGLE`), provider subject, and timestamps; the user's roles; and password reset tokens by hash with expiry and use time.

#### Scenario: Unique email
- **WHEN** two users with the same email differing only in case are inserted
- **THEN** the second insert is rejected by the database

#### Scenario: Unique provider subject
- **WHEN** two users with the same provider and provider subject are inserted
- **THEN** the second insert is rejected

#### Scenario: Cascade
- **WHEN** a user row is deleted
- **THEN** its roles and reset tokens are deleted with it

### Requirement: Health endpoints
The service SHALL expose liveness and readiness endpoints without authentication at `/auth/actuator/health/liveness` and `/auth/actuator/health/readiness`, readiness SHALL report not-ready while the database is unreachable, and no other actuator endpoint SHALL be reachable without authentication.

#### Scenario: Healthy
- **WHEN** the service is running and the database is reachable
- **THEN** liveness and readiness both respond 200 without a token

#### Scenario: Database down
- **WHEN** the database is unreachable
- **THEN** readiness responds 503 and liveness still responds 200

### Requirement: Configuration from environment
The service SHALL read the database connection, the issuer URL, the SPA redirect and post-logout URIs, the UI base URL for reset links, and the optional Google credentials from configuration, SHALL contain no real credentials in the repository, and SHALL fail to start when the issuer is missing outside the test profile.

#### Scenario: Missing issuer
- **WHEN** the service starts with no issuer configured and no test profile
- **THEN** startup fails with a message naming the setting

### Requirement: Local development environment
The `auth/` folder SHALL provide a documented command that starts a local PostgreSQL for `authdb`, and a documented command that builds and runs all its tests with only a container runtime and a JDK installed.

#### Scenario: Fresh checkout
- **WHEN** a developer follows `auth/README.md`
- **THEN** the service starts locally on port 9090 under `/auth` and the test suite passes
