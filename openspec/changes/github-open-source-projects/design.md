# Design

## Context

The profile is one JSON document per user (`profile` table, `ProfileJson`: strict on requests, lenient on stored rows), validated by `ProfileValidator`, edited by `ProfileForm.tsx`. Projects (type, links, highlight) were added in `experience-projects`; they could not separate work from open-source and were typed by hand or guessed by the CV reader. `sources/internal/PoliteHttp` is the only existing outbound HTTP code, but it belongs to the `sources` module (crawler) and is internal. See proposal.md for motivation and scope.

## Goals / Non-Goals

**Goals:**
- Open source work comes from a linked account, picked by the user, stored as a snapshot in the profile.
- A second provider needs one new class and one enum value.

**Non-Goals:**
- Tokens, OAuth, private repositories, refresh jobs, README or commit analysis (see proposal).

## Decisions

1. **Replace, do not keep, `projects`.** Decided by the owner. Reading stays lenient so old rows load (field ignored); strict request parsing rejects `projects`, so the UI and API change together. No Liquibase changeset: the document layout is additive plus one ignored field, `SCHEMA_VERSION` stays 1. Alternative: migrate OPEN_SOURCE entries — rejected, they have no repo identity.
2. **Snapshot in the profile, not a live fetch.** Drafting must only use facts from the profile, and the profile must not change when GitHub does. Picked repositories are copied (`fullName`, `name`, `url`, `description`, `language`, `topics`, `stars`) plus the user's `note`. Alternative: store only `fullName` and fetch at draft time — rejected: breaks the facts rule, adds failure modes, rate limits.
3. **Account is separate from picks.** `codeAccounts: [{provider, username}]` is the pointer used for loading; `openSourceProjects[]` holds picks, each with its own `provider`, so a user can mix providers later. `provider` is an enum (`GITHUB`); adding `GITLAB` is an enum value plus a username rule.
4. **Provider contract in `profile` module.** Public `RepositoryCatalog.list(provider, username): RepositoryListing` (sealed: `Found(list)`, `NotFound`, `RateLimited`, `Unavailable`, `InvalidUsername`) for `web` to call; internal `RepositoryProvider` interface (`provider`, `usernameRule`, `list`) with `GitHubRepositoryProvider`; `RepositoryCatalog` picks the bean by `provider`. Sits in `profile` because the username rules and snapshot shape are profile content and no new module is needed (modules list is fixed; `ApplicationModules.verify()` unaffected).
5. **HTTP client.** JDK `HttpClient` (as `PoliteHttp`; no new dependency) in the provider: base URL from configuration (`jobhunter.repositories.github.base-url`, default `https://api.github.com`), 5 s connect, 10 s read, body cap 2 MB, no redirects, `Accept: application/vnd.github+json`, `User-Agent` set, `X-GitHub-Api-Version` pinned. Request `GET /users/{username}/repos?type=owner&sort=pushed&per_page=100`, one page only (100 max). Username is validated first and URL-encoded as a path segment, so the request cannot reach another path or host.
6. **Failure mapping.** 404 → NotFound; 403/429 with `x-ratelimit-remaining: 0` (or 429) → RateLimited; timeouts, IO, 5xx, 403 otherwise, unparseable body → Unavailable. Only fixed error codes leave the service; nothing from the response or the username is logged (log outcome and duration only).
7. **Filtering.** Provider drops `fork` and `archived` repositories; the rest is returned in the provider's order. Parsing is lenient (ignore unknown fields; null description allowed).
8. **Cost control.** An in-process cache of 5 minutes per `(provider, username)` (Caffeine if already a dependency, else a small map with expiry) so repeated "Load" clicks do not burn the 60/h unauthenticated limit, plus the existing per-user request limiter style if one exists for API calls (task checks). No cache of user data, only public listings.
9. **CV reader.** Remove `ProjectDraft`, `ProjectLinkDraft`, `ProjectsSection`, `ProfileSection.PROJECTS`, the projects instruction and mapper code; keep experience `technologies`. Strict LLM answer parsing must ignore a stray `projects` field (scenario in llm spec).
10. **UI.** New `OpenSourceSection` component in `ui/src/profile/` (account field, repository checklist, picked list), `listRepositories` in a small `repositoriesApi.ts` through the existing API client; `TagInput` stays (used by experience). Remove project types, `blankProject`, project rules and fixtures. Fixture `full-profile.json` (backend and UI, kept in sync by the drift test) gets the Contract sample below.

## Contract (do not change)

Profile JSON additions: `"codeAccounts":[{"provider":"GITHUB","username":"octocat"}]`,
`"openSourceProjects":[{"provider":"GITHUB","fullName":"octocat/hello-world","name":"hello-world","url":"https://github.com/octocat/hello-world","description":"My first repository","language":"Kotlin","topics":["demo","kotlin"],"stars":42,"note":"Maintainer since 2022"}]`. `projects` is gone.
Listing response: `{"repositories":[{"provider":"GITHUB","fullName":"octocat/hello-world","name":"hello-world","url":"https://github.com/octocat/hello-world","description":"My first repository","language":"Kotlin","topics":["demo","kotlin"],"stars":42,"pushedAt":"2026-09-30T12:00:00Z"}]}`.
Enum `CodeProvider { GITHUB }`. Error codes: `invalid_username`, `unknown_provider`, `account_not_found`, `provider_rate_limited`, `provider_unavailable`.

## Risks / Trade-offs

- [Unauthenticated GitHub limit 60/h per egress IP is shared by all users] → 5-minute cache, one page per load, fixed 429 message; token support is a later change.
- [Snapshots go stale (stars, description)] → shown as a snapshot; the user can re-pick; no refresh in this change.
- [SSRF through username] → strict username rule before any call, fixed host from configuration, no redirects.
- [Existing users lose stored projects on next save] → accepted by the owner; release note in the deploy notes.
- [Username is personal data] → stored only in the profile document, never logged.

## Migration Plan

Deploy backend and UI together (the strict parser rejects `projects`; the old UI would fail to save). No schema migration. Rollback: redeploy previous images; stored documents with the new fields are read leniently by the old code.
