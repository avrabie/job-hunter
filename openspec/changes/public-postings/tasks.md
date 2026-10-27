# Tasks

## 1. Backend

- [x] 1.1 Extend `PostingSearch`/`PostingQuery` with optional first-seen bounds, a stable order (`first_seen desc, id`), a count for the window; check the query plan and add an index on `first_seen` (Liquibase) only if needed. Verify: tests for window bounds, tie order, count, canton and language filters.
- [x] 1.2 `PublicPostingsController` (`GET /api/public/postings`) with its own response type (no description, raw payload or last-seen), parameter validation (400 cases), cache header, OpenAPI text; permit the GET in `SecurityConfig`. Verify: API tests for each scenario, a posting with description and raw payload, no token needed, API-docs guard passes.
- [x] 1.3 In-memory per-client limiter (setting `jobhunter.postings.public.per-minute`, default 60) with bounded state and `Retry-After`; no client address in logs. Verify: tests for the 61st request, other client unaffected, eviction under many addresses, log-capture test.
- [x] 1.4 Docs: `postings/README.md`, PLAN.md, OpenAPI. Verify: wording matches the code.

## 2. UI

- [x] 2.1 Public client call without a token (`publicPostingsApi.ts`) and a landing section component; absent on failure, 429 or empty; text only. Verify: tests for 6 postings, failure, 429, empty, markup in a title, no other `/api` call, no description rendered.
- [x] 2.2 Update the landing page tests and `ui/src/README.md`; run tsc, oxlint `--deny-warnings`, prettier, vitest. Verify: all green.

## 3. Checks

- [x] 3.1 `./gradlew test integrationTest` including `ModularityTests`; `openspec validate public-postings`. Verify: all green.
- [ ] 3.2 Deploy backend and ui only after the owner says so; check the endpoint and the landing page on prod; archive after the owner tests.
