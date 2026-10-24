# Tasks

Rules for implementation agents (see `handoff/haiku-rules.md`): edit only the files named, copy existing patterns, run the verify command, tick the box, stop and report if blocked. Backend commands run in `backend/` (Docker must be running for the integration tier), UI commands in `ui/`. The facts in `design.md` Context are measured on 2026-10-24 and are the baseline to beat.

Groups 1 and 2 are read-only investigations and can run in parallel; group 3 needs 1 and 2; groups 4 and 5 are read-only analysis and can run in parallel with everything; group 6 needs 4 and 5 and the owner's choices.

## 1. Measure and find flaky tests (read-only, reports only)

- [ ] 1.1 Run `./gradlew clean verify` five times in `backend/` (before any change, so first run `git status` is clean) and record per run: duration, failures (class, test, message). Also run `integrationTest`-equivalent (all tests) twice with the machine under load (for example while a second Gradle build compiles). Write `handoff/flaky.report.md` (under 80 lines): a table of every failure with its run, the likely cause by reading the test (sleep, port, shared state, order, Docker), and a proposed fix per test. Do not edit code.
- [ ] 1.2 From `backend/build/test-results/test/*.xml` build a table of all test classes with test count, time and whether they use `@IntegrationTest`, their own `@SpringBootTest`, a Testcontainer, `Thread.sleep`/`delay`/`await`. Count distinct Spring contexts created in one full run (cache log). Write `handoff/slow.report.md` (under 80 lines) with the 15 slowest classes, the cause of each and a proposed fix with the expected saving. Do not edit code.

## 2. Deprecation inventory (read-only)

- [ ] 2.1 Collect every deprecation as described in `design.md` decision 5 for `backend/`, `auth/`, `gateway/` and `ui/` (compiler with `--rerun-tasks`, `--warning-mode all`, startup logs of each app, IDE inspections through the IDE MCP tools if available, dependency notes, UI test and build output). Write `docs/deprecations.md`: a table (file:line, API or setting, replacement, risk, fix now or leave and why). Do not edit code.

## 3. Test tiers and speed (after 1 and 2)

- [ ] 3.1 In `backend/src/test/kotlin/net/s4v3/backend/IntegrationTest.kt` add `@Tag("integration")` to the meta-annotation, and add the tag to every other test class that starts a Spring context or a container by hand (list them in the report). In `backend/build.gradle.kts` define `test` (excludes the tag), `integrationTest` (includes it, `shouldRunAfter(test)`, one fork) and `verify` (depends on both); `build` depends on `test` only. Verify: `./gradlew clean build` runs only the fast tier and passes; `./gradlew integrationTest` passes; `./gradlew verify` runs the sum (the counts add up to 573 or more); report the three durations.
- [ ] 3.2 Fix each flaky test of `handoff/flaky.report.md` at its root cause (one task per test, named in the report), each verified by running that class 10 times (`./gradlew test --tests "<Class>" --rerun` in a loop). Verify: five consecutive `./gradlew clean verify` runs have no failure.
- [ ] 3.3 Apply the fixes of `handoff/slow.report.md` in order of saving: injectable durations (the CV export busy wait first), shared contexts, one container. Verify per fix: the class passes and its time drops as predicted; at the end `./gradlew build` is under 20 s of test time and `./gradlew verify` under 90 s (report real numbers).

## 4. Deprecation fixes (after 2)

- [ ] 4.1 Fix every item of `docs/deprecations.md` marked "fix now", one item per commit-sized step, each with the tests of the touched code passing; for the items marked "leave", add a `@Suppress("DEPRECATION")` with a one-line reason naming the library version only where a warning would otherwise remain. Verify: `./gradlew compileKotlin compileTestKotlin --rerun-tasks` prints no `w:` line (or only listed, justified ones), and `./gradlew verify` passes.

## 5. Code map (read-only analysis plus docs)

- [ ] 5.1 Write `docs/architecture.md` and `docs/glossary.md` for the backend as described in `design.md` decision 6 (diagram in Mermaid or ASCII, module map, flows with class names and file paths, data model from the Liquibase changelogs, security model from the specs and config, configuration index of every `jobhunter.*` and `app.*` property with default and meaning, where the tests live). Every statement cites a file path; unverified ones are labelled. Verify: a second reader (the lead) spot-checks 10 statements against the code.
- [ ] 5.2 Write a `README.md` of at most 40 lines in each backend module package (`accounts`, `cv`, `files`, `llm`, `matching`, `postings`, `profile`, `sources`, `web` under `backend/src/main/kotlin/net/s4v3/backend/`) and one each for `auth/`, `gateway/` and `ui/src/` folders that lack one: purpose, public API, internal parts, events published and consumed, tables, tests, and the date and files it was read from.
- [ ] 5.3 Write `docs/simplification-candidates.md`: ranked candidates (dead or unused code found by searching for unreferenced symbols, duplicated patterns, forwarding-only layers, long classes, misleading names, tests that duplicate each other), each with file paths, proposed change, effort (S/M/L) and risk, and for tests the coverage they add that others do not. Verify: every candidate names real files and a grep showing why it is dead or duplicated.

## 6. Owner choices and root README (after 4, 5 and the owner reading the candidates)

- [ ] 6.1 Add the command table of `design.md` decision 8 to the root `README.md` and link `docs/architecture.md`, `docs/glossary.md`, `docs/deprecations.md` and the module READMEs. Verify: each command in the table was run once and works.
- [ ] 6.2 The owner picks candidates from `docs/simplification-candidates.md`; each picked one becomes its own tracked task here (or a separate change when it alters behaviour) before anything is changed.

## Workflow follow-up

- The lead reviews every agent's report and diff, runs `./gradlew clean verify` five more times at the end to confirm there are no flaky tests, deploys only if main code changed, and archives the change. Pruning tests (deleting low-value ones) is a separate decision after the owner has seen the tier timings.
