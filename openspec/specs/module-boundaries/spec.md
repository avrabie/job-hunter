# module-boundaries Specification

## Purpose
Defines the module layout of the modular monolith and the rules that keep modules decoupled, so later features can be added inside one module without reaching into others.

## Requirements

### Requirement: Fixed module set
The system SHALL consist of exactly these top-level modules: `sources`, `postings`, `accounts`, `profile`, `matching`, `applications`, `llm`, `web`, `notifications`. Each module SHALL separate its public API from its internal implementation.

#### Scenario: Modules detected
- **WHEN** the application module structure is analysed
- **THEN** it contains exactly the nine listed modules and no others

#### Scenario: Internal types are hidden
- **WHEN** module A references a type that module B keeps internal
- **THEN** the boundary verification fails and names both modules

### Requirement: Boundary verification in the build
The build SHALL fail when a module accesses another module's internals or when modules form a dependency cycle.

#### Scenario: Clean structure
- **WHEN** the test suite runs on the committed code
- **THEN** the module verification passes

#### Scenario: Dependency cycle
- **WHEN** module A depends on module B and B depends on A
- **THEN** the module verification fails

### Requirement: Koog confined to the llm module
Only the `llm` module SHALL depend on the Koog libraries; other modules SHALL use LLM features only through the public API of `llm`.

#### Scenario: Koog used elsewhere
- **WHEN** a class outside `llm` imports a Koog package
- **THEN** the build fails

### Requirement: Event publication registry
The system SHALL persist every published module event until all its listeners have completed, so that events survive an application restart.

#### Scenario: Event survives a crash
- **WHEN** an event is published, a listener has not yet completed, and the application restarts
- **THEN** the event is still recorded as incomplete after restart

#### Scenario: Completed event
- **WHEN** all listeners of an event complete successfully
- **THEN** the event is marked completed

### Requirement: Module events are documented contracts
Each module event SHALL be specified in the owning capability's spec with its name, payload, publisher and consumers, and SHALL carry identifiers rather than personal or CV content.

#### Scenario: Event with a documented contract
- **WHEN** a module event exists, such as `UserRegistered`
- **THEN** its name, payload, publisher and consumers are written in the owning capability's spec (a review-time rule checked before archiving, not by a test)
