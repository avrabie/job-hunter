# Tasks

## 1. Storage and matching (applications)

- [x] 1.1 Liquibase `013`: table `letter_skill_analysis` (application_id PK/FK cascade, owner_sub, analysis jsonb, notes jsonb, created_at) with rollback. Verify: migration test applies and rolls back; deleting an application deletes the row.
- [x] 1.2 `SkillMatcher` (normalise + alias table) and the include/gaps/other split with related-name filtering. Verify: unit tests for case, punctuation, aliases, a claimed match with no saved name, a related name not in the profile.
- [x] 1.3 Repository and service: store/replace analysis, read derived result, save notes (max 20, 500 chars, plain text, only current gaps), keep matching notes on re-analysis. Verify: service tests incl. user A cannot read or write user B's rows (404) and 400 for a non-gap note.

## 2. Model call (llm)

- [x] 2.1 `SkillExtractor` interface and plain types in `llm`; `KoogSkillExtractor` with manual JSON structure, description as quoted data, names only, fixed failures. Verify: tests with the fake executor record the request: only description and skill names, injection text stays quoted; bad JSON gives the failure.
- [x] 2.2 `LetterRequest.gapNotes` and the prompt block "written by the candidate" with the no-claim rule. Verify: request-recording test: notes present and marked, gap skill absent from candidate skills, "ignore previous instructions" in a note stays data; no notes behaves as before.

## 3. API (web, applications)

- [x] 3.1 Endpoints GET analysis, POST analyse, PUT notes with codes 404/400/429/502/503, setting `jobhunter.letters.skills-per-hour`, separate limiter. Verify: API tests for the scenarios, 21st analysis is 429 while letters still work and the reverse, OpenAPI guard test passes.
- [x] 3.2 Letter generation reads the application's notes server-side. Verify: API test: note saved, then generate, the recorded request contains it; a body cannot supply notes.
- [x] 3.3 Privacy: marker words in description, skills and notes never appear in logs or error bodies. Verify: log-capture test.

## 4. UI

- [x] 4.1 `skillsApi.ts` and `SkillsStep` in the workspace: analyse button, three lists, tick state, notes with counter, save, fixed messages, optional skip with the old picker. Verify: component tests for defaults, ticking, note save, 503/429/502 messages, no profile request.
- [x] 4.2 Send ticked skills with the letter request. Verify: test that the request body has the ticked indexes and no note text.

## 5. Wrap-up

- [x] 5.1 Docs: `applications/README.md`, `llm/README.md`, `application.yaml` comment for the new setting, PLAN.md line. Verify: docs mention the endpoints and the separate limit.
- [x] 5.2 `./gradlew test integrationTest`, `ApplicationModules.verify()`, UI checks (vitest, tsc, oxlint, prettier), `openspec validate --all`.
- [x] 5.3 Deploy backend and ui only after the owner says so; archive after the owner tests on prod.
