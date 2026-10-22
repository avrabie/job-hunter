# Proposal

## Why

The shared store now fills itself every hour, but nothing connects a posting to a person. Scoring every posting for every user with Claude would cost money and time for postings that cannot fit (wrong canton, wrong workload, nothing like the person's target roles). A cheap, deterministic prefilter decides which postings are worth a closer look for each user, ranks them, and gives the later Claude scoring a short list. It also gives the owner something visible now: "which of the ingested postings fit my profile".

## What Changes

- Create the `matching` module with a **prefilter**: for one user's profile and one posting it computes `passed`, a relevance `score` (0-100) and short `reasons` (fixed codes such as `CANTON_MISMATCH`, `ROLE_MATCH`). No Claude call, no embeddings.
- **Hard rules** reject a posting for a user when it is out of scope (French, Italian or unknown language), when its canton is known and not among the user's preferred cantons (if any), when its workload range is known and does not overlap the user's, when its remote mode is known and not among the user's accepted ones, or when its top salary is known and below the user's minimum. A posting whose value is unknown never fails that rule (most current postings have no workload, remote mode or salary), but the reason `UNKNOWN_<field>` is recorded.
- **Relevance score** from three signals: the posting title against the user's target roles (and recent job titles when no target roles are set), how many of the user's skills appear in the posting text as whole words, and whether the posting language is one the user speaks at B2 or above. A posting passes when no hard rule fails and its score reaches a threshold (setting, default 30).
- **Store results** per user and posting in a new table `posting_match` (passed, score, reason codes, the profile version used, evaluation time). The table holds no profile text and no posting text, only ids, numbers and codes.
- **Evaluate automatically**: when `postings` publishes `JobIngested`, evaluate that posting for every user who has a stored profile; when a user saves a profile, a new `ProfileSaved` event makes `matching` re-evaluate all postings for that user.
- **Read endpoint** `GET /api/matches?page=&size=` returns the caller's passing matches, best score first, each with the posting's title, company, city, URL, score and reasons. Everything is scoped to the token's `sub`.
- `profile` gains a public `ProfileSaved` event and a way for other modules to read all stored profiles page by page; `postings` documents that `matching` now consumes `JobIngested`.

### Observed about the current code

- `JobIngested(postingId, ingestedAt)` exists and is recorded by the event publication registry; it has no consumer. `PostingQuery` returns postings without the raw payload and has `find` and `list`.
- The profile holds preferences (`targetRoles`, `workloadMin/Max`, `remoteModes`, `cantons`, `minSalaryChf`), `skills`, `languages` with levels, and experience titles. `ProfileService.replace` saves a profile but publishes no event; there is no public listing of profiles.
- Current Greenhouse postings carry title, company, description, city and canton, language and the out-of-scope flag, but no workload, remote mode or salary. So today the hard rules that matter are language and canton; the others become useful with the next sources.
- Per-user tables here reference `app_user(sub)` with `ON DELETE CASCADE`, so account deletion removes the user's data (pattern of `profile`).

### Out of scope

- Claude scoring, embeddings and pgvector, quotas and usage tracking (later changes in the matching phase).
- A UI page for matches, notifications and digests.
- Learning from user feedback ("not interested"), manual overrides of a match.
- Matching on nationality or work authorisation: the prefilter never rejects on `workAuthorisation`, because postings do not state it reliably.
- Changing any source or posting data.

## Capabilities

### New Capabilities
- `matching`: the prefilter rules and score, the stored per-user results, the triggers, and the matches endpoint.

### Modified Capabilities
- `profile`: adds the `ProfileSaved` event and read access to all stored profiles for other modules.
- `postings`: `JobIngested` now has a consumer (`matching`).

## Impact

- **Modules:** new `matching` (depends on `postings`, `profile` public APIs and `accounts` for `CurrentUser`); `profile` (event, listing); `postings` (spec text only). No cycles: `profile` and `postings` do not depend on `matching`.
- **Database:** changeset `009-posting-match` in a new file (`posting_match`, primary key (user, posting), foreign keys to `app_user` with cascade and to `posting`).
- **Dependencies:** none new.
- **Personal data:** the stored results are derived from the profile and are personal data: deleted with the account, never logged, and they hold no profile text (only numbers and fixed codes). Logs hold counts and ids only.
- **Per-user scoping:** every read and write is by the token's `sub`; a scenario proves user A cannot see user B's matches. **Claude cost:** none; the prefilter exists to limit it later.
