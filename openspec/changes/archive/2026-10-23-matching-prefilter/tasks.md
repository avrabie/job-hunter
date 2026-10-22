# Tasks

## 1. Profile and posting contracts

- [x] 1.1 Publish `ProfileSaved(userId, version)` from `profile` after a successful `replace`, delivered through the event publication registry, and add `ProfileDirectory.page(afterSub, size)` (keyset paged by owner sub, stored profiles only) as public API of `profile`. Verify with integration tests: a valid save publishes exactly one event with the user id and new version; a validation failure and a version conflict publish none; the payload has only those two fields; the directory returns exactly the users who saved a profile, in order, across two pages; no HTTP path serves it.

## 2. Schema

- [x] 2.1 Add `backend/src/main/resources/db/changelog/006-matching.sql` with changeset `009-posting-match` (`posting_match` with the primary key `(user_sub, posting_id)`, foreign keys to `app_user(sub)` and `posting(id)` both `ON DELETE CASCADE`, the `(user_sub, passed, score DESC)` index) with `--rollback`, include it in `db.changelog-master.yaml`, and raise the changeset count in `DatabaseBaselineTests` to 9 with a check for the new table. Verify with a schema test (a second row for the same pair is rejected; a row for an unknown user or posting is rejected; deleting the user deletes the rows) and that `DatabaseBaselineTests` passes, including the "running again changes nothing" test.

## 3. The prefilter

- [x] 3.1 Implement `Prefilter` (hard rules, relevance score, reason codes, whole-word matching with escaped skills, stop-word handling, workload overlap) as a plain class in `matching.internal`. Verify with unit tests for every scenario in the spec: wrong canton, unknown canton, no preference, workload overlap and non-overlap, remote mismatch, salary below minimum, out of scope, work authorisation unused, title and skills giving 100, partial credit (title 25, skills 24), no overlap giving `BELOW_THRESHOLD`, whole words ("Go" versus "good" and "going"), skills with symbols (`C++`, `C#`), reasons are codes only, and a changed threshold.

## 4. Storage and triggers

- [x] 4.1 Implement the results store (upsert per pair, read of passing results joined to postings, ordering and paging) and the user-scoped read. Verify with integration tests: one row per pair after two evaluations holding the latest result; no row stored for a user without a profile (through the evaluation service); ordering by score then newest posting; paging and the 100 cap; failed results are not listed.
- [x] 4.2 Implement the `JobIngested` listener (page through stored profiles, evaluate, upsert, isolate per-user failures, rethrow only when all fail). Verify with integration tests: two users with profiles each get a result for a new posting; redelivery leaves one row each; one user's evaluation failing (a profile that makes the evaluator throw, injected through a test double) still stores the others.
- [x] 4.3 Implement the `ProfileSaved` listener (read the saved profile, page through postings by 100, upsert, stop early if a newer version exists). Verify with integration tests: after a save that prefers canton GE every posting in another known canton does not pass for that user; another user's results are unchanged; a save made while an earlier evaluation runs ends with results for the newer version.

## 5. Endpoint

- [x] 5.1 Implement `GET /api/matches` (`page`, `size` default 20, cap 100, token `sub` only). Verify with `@ApiTest` tests: no token gives 401; user A receives exactly A's passing results and user B exactly B's (the per-user scoping scenario); ordering and paging; `size=1000` returns at most 100; the items hold the listed fields and no profile data; non-numeric `page` gives 400.
- [x] 5.2 Add the log-capture test, in the style of `PostingLoggingTests`, with markers in a profile's skill and role names and in a posting's title and description, asserting no marker reaches the logs during a posting evaluation and a profile re-evaluation, and that no line holds a user id next to a score.
- [x] 5.3 Verify `ModularityTests` passes with `matching -> postings`, `matching -> profile` and `matching -> accounts`, and that `profile` and `postings` do not import `matching`.

## 6. Docs and integration

- [x] 6.1 Document in `backend/README.md` the rules, the score, the threshold setting, the triggers, the endpoint with an example, the limits (no Claude, unknown values never reject, re-save the profile to evaluate existing postings), and update the `matching` row in `PLAN.md`. Verify the setting name matches the code.
- [x] 6.2 Run the full backend build (`./gradlew build` in `backend/`) and verify it passes.

## Workflow follow-up

- Build and push the backend image, bump the tag in `k8s/overlays/prod/kustomization.yaml`, preview with `kubectl --context s4v3 diff -k k8s/overlays/prod`, apply, and confirm the backend is Ready (changeset `009` ran).
- With the user's go-ahead: the user saves their profile once on s4v3.net and calls `GET /api/matches` (the OpenAPI page makes this easy once deployed); check the logged counts. Note the deploy in `k8s/README.md`.
- Archive the change (sync `matching`, `profile` and `postings`), then propose Claude scoring on the prefilter's short list.
