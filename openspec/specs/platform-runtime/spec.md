# platform-runtime Specification

## Purpose
Defines the runtime baseline of the backend: database and migrations, health checks, and a reproducible local and test environment.

## Requirements

### Requirement: Versioned database schema
The system SHALL create and change its PostgreSQL schema only through ordered, versioned migrations that run at startup, and SHALL fail to start when a migration fails.

#### Scenario: Empty database
- **WHEN** the application starts against an empty database
- **THEN** all migrations are applied and the application becomes ready

#### Scenario: Up-to-date database
- **WHEN** the application starts against a database that already has all migrations
- **THEN** no migration is re-applied and the data is unchanged

#### Scenario: Failing migration
- **WHEN** a migration fails
- **THEN** the application does not start and the error names the failing migration

### Requirement: Vector support
The database baseline SHALL enable the `vector` extension so later changes can store and query embeddings.

#### Scenario: Extension present
- **WHEN** the baseline migration has run
- **THEN** the `vector` extension is installed and a column of type `vector` can be created

### Requirement: Health endpoints
The system SHALL expose liveness and readiness endpoints without authentication, and readiness SHALL report not-ready while the database is unreachable. No other actuator endpoint SHALL be reachable without authentication.

#### Scenario: Healthy
- **WHEN** the application is running and the database is reachable
- **THEN** liveness and readiness both respond 200 without a token

#### Scenario: Database down
- **WHEN** the database is unreachable
- **THEN** readiness responds 503 and liveness still responds 200

#### Scenario: Other actuator endpoints
- **WHEN** an unauthenticated request calls an actuator endpoint other than health
- **THEN** the system responds 401 or 404

### Requirement: Configuration from environment
The system SHALL read the database connection and the auth service issuer URI from configuration, SHALL NOT contain credentials in the repository, and SHALL fail to start when the issuer URI is missing outside the test profile.

#### Scenario: Missing issuer
- **WHEN** the application starts with no issuer URI configured and no test profile
- **THEN** startup fails with a message naming the missing setting

### Requirement: Local development environment
The repository SHALL provide a single documented command that starts a local PostgreSQL with pgvector, and a single documented command that builds and runs all tests from a clean checkout with only a container runtime and a JDK installed.

#### Scenario: Fresh checkout
- **WHEN** a developer follows the README on a clean checkout
- **THEN** the application starts against the local database and the test suite passes

### Requirement: Personal data kept out of version control
The repository SHALL ignore the `private/` directory so real CVs and letters cannot be committed.

#### Scenario: File in private
- **WHEN** a file is created under `private/`
- **THEN** version control reports it as ignored
