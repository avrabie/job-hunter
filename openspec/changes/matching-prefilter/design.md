# Design

## Context

`postings` publishes `JobIngested(postingId, ingestedAt)` for each new canonical posting and exposes `PostingQuery` (`find`, `list(page, size)`, no raw payload). `profile` stores one JSON document per user in `profile(owner_sub → app_user(sub) ON DELETE CASCADE)` and exposes `ProfileService.get/replace`; it publishes no event and offers no listing. `accounts` provides `CurrentUser`. Modulith's event publication registry stores listener work in `event_publication`; `@ApplicationModuleListener` listeners are asynchronous, transactional and retried on restart. The `matching` module package does not exist yet. Greenhouse postings currently have city, canton, language and the out-of-scope flag; workload, remote mode and salary are unknown.

## Goals / Non-Goals

**Goals:**
- A deterministic, explainable prefilter that cuts the postings a later Claude step must look at, stored per user and kept current.
- A visible result: the caller's matches over HTTP.

**Non-Goals:**
- Claude scoring, embeddings, quotas, a UI, feedback learning.

## Decisions

### Pure rules in a plain class
`Prefilter.evaluate(profile: Profile, posting: Posting, threshold: Int): MatchResult(passed, score, reasons)` in `matching.internal`: no database, no Spring, no clock. Unit tests cover every scenario of the spec. The profile and posting types come from the public APIs of `profile` and `postings`.

- *Whole-word matching:* skills and role words are matched with Unicode-aware word boundaries, case-insensitive, over title and description (`(?<![\p{L}\p{N}])skill(?![\p{L}\p{N}])`), so "Go" does not match "good". Skills with symbols (`C++`, `C#`, `.NET`) use the same lookarounds around the quoted text, so they match as literal strings between non-word characters.
- *Role words:* a target role is split into words of at least three letters (stop words like `and`, `of`, `the`, `und`, `der`, `für` dropped); the title part is `50 × matchedWords / totalWords`, taking the best of the user's roles. Without target roles, the titles of the user's experience entries are used the same way.
- *Skills part:* `40 × min(hits, 5) / 5`, rounded down. The number of hits is capped so a skill-heavy profile does not rank everything high.
- *Language part:* 10 when the posting language is one of the profile's languages at B2 or above (`B2`, `C1`, `C2`, `NATIVE`).
- *Workload overlap:* ranges `[a,b]` and `[c,d]` overlap when `a ≤ d` and `c ≤ b`; a missing end of the user's range is treated as 0 and 100.
- *Alternative:* a weighted learned model. No training data exists; rules are explainable and testable.
- *Alternative:* embeddings now. They need the `llm` module and pgvector content that do not exist yet; the stored `score` leaves room to combine later.

### Results table
`posting_match(user_sub TEXT, posting_id UUID, passed BOOLEAN, score INT, reasons TEXT[] , profile_version BIGINT, evaluated_at TIMESTAMPTZ)`, primary key `(user_sub, posting_id)`, `user_sub` references `app_user(sub) ON DELETE CASCADE`, `posting_id` references `posting(id) ON DELETE CASCADE`; indexes `(user_sub, passed, score DESC)`. Changeset `009-posting-match` in a new file `006-matching.sql`. Upsert on the primary key makes evaluation idempotent. Rows are stored for failures too (so a re-evaluation can tell "rejected" from "never evaluated") but the endpoint lists passing rows only.

### Triggers
- `@ApplicationModuleListener` on `JobIngested`: load the posting, then page through stored profiles (`ProfileDirectory.page(after, size)` below) and upsert one result per user. A failure for one user is caught and counted; the listener finishes the others, and rethrows only when every user failed so the registry retries the event.
- `@ApplicationModuleListener` on `ProfileSaved(userId, version)`: read that user's profile with `ProfileService.get`, then page through postings with `PostingQuery.list(page, 100)` and upsert. If the profile version moved on in the meantime (a newer save), the newer event does the work and the older run stops early by comparing versions between pages.
- Both listeners hold no long transaction: each page is its own transaction, so a large posting table does not hold a connection for the whole run.

### Changes in `profile` and `postings`
- `profile` publishes `ProfileSaved(userId, version)` from `replace` after a successful save, using the event publication registry so it is delivered after commit (publishing inside the saving transaction with a transactional listener). It carries no content.
- `profile` exposes `ProfileDirectory.page(afterSub: String?, size: Int): List<OwnedProfile>` (owner sub plus `StoredProfile`) ordered by `owner_sub`, keyset paged. It is an interface in the module's public package; there is no controller for it.
- `postings` changes only its spec text (a consumer exists now).

### The endpoint
`MatchesController` in `matching.internal`: `GET /api/matches?page=&size=` using `CurrentUser.id()` only; the query joins `posting_match` to `posting` for the title, company, city, canton and URL, filters `passed`, orders by `score DESC, first_seen DESC`, and pages with a size cap of 100. The response items carry the posting id, title, company, city, canton, URL, score and reasons. No raw payload, no profile. If `openapi-docs` is applied first, the controller needs a summary (its guard test enforces it).

### Settings
`jobhunter.matching.threshold` (default 30). No other setting.

### Module boundaries
`matching` depends on `postings` (public API), `profile` (public API) and `accounts` (`CurrentUser`). `profile` and `postings` do not depend on it. `ApplicationModules.verify()` must pass; the new table is owned by `matching`.

### Logging
Per evaluation run: counts (users evaluated, passed, failed) and the posting id or user count, never profile content, role or skill names, scores next to a user id, or reason codes with a user id. A log-capture test proves it.

## Risks / Trade-offs

- [A keyword prefilter misses good postings (titles use other words) or passes bad ones] → it is deliberately generous (threshold 30, unknown values never reject) and its stored scores let the threshold be tuned; Claude scoring will see only what passes, so misses are the cost, which the setting and reason codes make visible.
- [A profile save re-evaluates every posting (cost grows with the table)] → pages of 100 with per-page transactions; thousands of postings are milliseconds of rule evaluation each; revisit with a "changed fields" check if it ever matters.
- [Many users × one posting on `JobIngested`] → same paging; currently a handful of users.
- [Skill names with regex metacharacters] → quoted with `Regex.escape` before the lookarounds.
- [Results hold derived personal data] → cascade-delete with the account, no text, never logged.
- [Event delivery is asynchronous, so a fresh profile save is not instantly reflected] → acceptable; the endpoint shows the state as of the last evaluation. The listener and registry retry after a crash.
- [Most postings today lack workload, remote mode and salary, so those rules rarely bite] → recorded as `UNKNOWN_*` reasons; they start to matter with sources that publish them.

## Migration Plan

New table only. Deploy the backend. Existing postings (40 today) and existing profiles are not evaluated by events that already happened: after deploy, a profile save triggers a full evaluation for that user, and new postings trigger evaluation for all users. A one-time backfill for profiles saved earlier is the profile owner re-saving (documented); no backfill job is built. Rollback: redeploy the previous image; the table can stay.

## Open Questions

- Whether the threshold should be per user later (a "strictness" preference) is left to the matching UI change.
