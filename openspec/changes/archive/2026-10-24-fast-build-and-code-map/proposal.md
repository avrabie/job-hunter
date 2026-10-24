# Proposal

## Why

`./gradlew clean build` in `backend/` takes about 1.5 minutes for 573 tests, one test failed once for no obvious reason (a flaky test), IDE inspections show many deprecated API uses, and the code base (about 6,500 lines of backend code, 10,000 lines of backend tests, plus auth, gateway and UI) has grown faster than the owner's mental model of it. The owner wants a build that is fast and trustworthy, fewer deprecation warnings, and a clear map of the code.

## What Changes

- **Test tiers, nothing deleted.** Tests are split into a **fast tier** (`./gradlew test`: unit tests, no Spring context, no Docker) and an **integration tier** (`./gradlew integrationTest`: Spring context and Testcontainers, tagged `integration`). `./gradlew build` runs the fast tier only; `./gradlew verify` runs both and is what runs before a deploy. The 30 classes that use the `@IntegrationTest` annotation get the tag from that one annotation.
- **Flaky tests found and fixed.** The suite is run repeatedly to find tests that fail intermittently; each one gets a root-cause fix (shared state, ports, time, ordering, Docker timing), not a retry.
- **Slow tests made fast.** The slowest classes (11 s, 9 s, 8 s, 7 s) are examined; real waits become injectable durations, and Spring contexts are shared instead of rebuilt where tests only differ by a property.
- **Deprecations.** A written inventory of every deprecation (compiler, IDE inspections, Gradle, dependencies, startup logs of the apps, UI tooling). Those with a direct replacement are fixed; the rest are listed with a reason.
- **Code map.** New `docs/architecture.md` (modules, main flows, data, security, configuration, where the tests are), `docs/glossary.md`, a short `README.md` per backend module, and `docs/simplification-candidates.md`: a ranked list of dead code, duplicated patterns and over-abstraction with effort and risk. **No simplification is applied without the owner choosing it.**
- One command table (build, test, run, deploy checks) in the root README.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
None. This change does not alter behaviour; it has no spec deltas (`skip_specs`).

## Impact

- Files: `backend/build.gradle.kts` (test tasks), test sources (tags, fixtures, a few injected durations), `docs/`, per-module READMEs, root `README.md`, small deprecation fixes in main and test code.
- Behaviour and API: unchanged. Each deprecation fix is covered by the existing tests of that code.
- Risk: the fast tier by itself covers less; mitigated by `verify` before every deploy and by listing which spec scenarios are covered only by integration tests.

## Out of scope

Deleting tests (decided: split, do not delete; pruning can follow once the tiers exist and the owner has seen the numbers), upgrading Spring Boot or other major dependencies, refactoring code the owner has not chosen from the candidates list, changes to auth, gateway or UI behaviour, CI setup.
