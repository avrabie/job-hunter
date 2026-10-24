# Tasks

Rules for every task (see `handoff/haiku-rules.md`): edit only the files named, copy the pattern of the existing code named, run the verify command, tick the box, stop and report if blocked. Backend commands run in `backend/` (test tiers: `./gradlew test` is the fast tier; API and Spring tests are integration-tagged and run with `./gradlew integrationTest --tests "<Class>"` using double quotes; other agents run Gradle at the same time, so run only the classes you touch; the lead runs `verify` at the end), UI commands in `ui/` (`npx`; install with `npx --yes pnpm@12.14.0 install --frozen-lockfile` if `node_modules` is missing, never commit it). The Contract in `design.md` is binding. Test data is synthetic (an invented "Ada Example", "Employer1 AG", invented job texts); never use real personal data or open `private/`. Everything must also work in the Linux amd64 image.

Groups 1 and 2 are independent and can run in parallel; group 3 needs 1 and 2 merged; groups 4 and 5 need the Contract only and can start with group 1 (backend not needed to write them); group 6 last.

## 0. Checks by the lead

- [ ] 0.1 After group 2 is merged, run the real model once with a synthetic job description and synthetic profile (locally with the key, never committed) and judge the letter's quality, length, invention and language; adjust `LetterPrompts` if needed and record the result in `design.md` Risks.
- [ ] 0.2 Decide with the owner whether `jobhunter.letters.model` stays Haiku or moves to Sonnet.

## 1. Applications (backend)

- [ ] 1.1 Two Liquibase changesets (next ids after `moldo:015`: `application`, `motivation_letter`, per design decision 2; update the `DatabaseBaselineTests` changeset count and table assertions) and the repository.
- [ ] 1.2 `ApplicationService` (public API) and its implementation: track (idempotent, frozen copy from `PostingQuery.find`, live status from the posting's `lastSeen` and content hash; add the hash to `Posting` minimally if missing), get, list with stage filter, patch (stage, notes, appliedOn with rules of the spec), remove, by-posting lookup, limit of 500. Replace the Java placeholder if the module rules require it.
- [ ] 1.3 `ApplicationController` with the endpoints of design decision 3 (without letters), OpenAPI summaries (the `ApiDocsContentTests` guard), errors via `ErrorResponses`. Tests (integration): every scenario of the `applications` spec including another user's id 404, idempotent track, frozen description after the posting changes, 501st conflict, stage rules, logs contain no description or notes (`captureAppLogs`). Update `applications/README.md` (new), `docs/architecture.md`, `ModularityTests` if needed.

## 2. LetterWriter (backend, `llm`)

- [ ] 2.1 `LetterWriter` interface and request types in `net.s4v3.backend.llm`, internal `KoogLetterWriter` plus `LetterPrompts`, settings `jobhunter.letters.model` (default empty = use `jobhunter.llm.model`), output cleaning per design decision 5, `llm/README.md`. Tests with `FakePromptExecutor`: delimiters around the description, instructions outside the data block, only given candidate facts present, previous letter and comments present for regeneration, injection text stays inside the data block, truncation at 20 000, cleaning (code fence, HTML refused, empty refused, over-length refused), `LlmFailure` mapping, no prompt or answer text in exception messages, and a `LlmLoggingTests`-style check.

## 3. Letters (backend, `applications`)

- [ ] 3.1 Letter service and endpoints (generate, regenerate, edited, list, read, download, delete), selection resolving against the saved profile (indexes), `paragraphs` function, `LetterLimiter` (`jobhunter.letters.per-hour`), 20-version cap, error mapping (503 `letter_not_configured`, 502 `letter_failed`, 429). API tests with a fake `LetterWriter`: all scenarios of the `motivation-letter` spec, the request the writer receives (chosen parts only, no contact data except name, no preferences), others' ids 404, comments stored with the new version, regeneration leaves the old version unchanged, limit 429 on the 21st call, download bytes and headers, logs contain no description, profile, comments or letter text.

## 4. UI: tracking (can start after the Contract is fixed)

- [ ] 4.1 `ui/src/applications/applicationsApi.ts` (+ tests, same style as `ui/src/files/filesApi.ts`), `ApplicationsPage.tsx` (grouped by stage, stage change from the row, warning mark), `InterestedButton.tsx` and its use in `ui/src/postings/PostingsPage.tsx` and `PostingPage.tsx` (stages loaded with `by-posting`), routes in `App.tsx`, nav entry in `Shell.tsx`. Tests for each scenario of `ui-applications` (Job Tracking page) and `ui-postings`.
- [ ] 4.2 `ApplicationPage.tsx`: frozen description, link, notice, stage, dates, notes save, remove with confirmation. Tests.

## 5. UI: letter workspace (needs the endpoint shapes of the Contract)

- [x] 5.1 `LetterWorkspace.tsx`: selection form from the saved profile, instructions, generate, text shown per numbered paragraph as text, per-paragraph and general comments, Regenerate, Edit and save as new version, Copy, Download, version list, the checking notice and the fixed messages for 429, 502 and 503; the TypeScript `paragraphs` function with the shared case table. Tests for every scenario of the spec.

## 6. Lead

- [ ] 6.1 Merge, full `./gradlew clean verify`, UI tests/tsc/oxlint/prettier, `openspec validate --all`, an independent security and privacy review (IDOR on applications and letters, prompt injection handling, logs, limits), docs (`docs/architecture.md`, `docs/glossary.md`, module READMEs), then deploy only after the owner says so; archive after the owner tests.
