# Proposal

## Why

The Projects section mixes work assignments with open-source work, and the user types every entry by hand or lets the CV reader guess. Open-source work already lives on a code-hosting account; the user should link that account, pick the repositories worth showing, and keep work experience out of this section.

## What Changes

- **BREAKING** Remove the `projects` list (and the `ProjectType`, links and highlight fields) from the profile, the CV draft, `section=projects` of `POST /api/cv/extract`, and the UI "Projects" section with its "Fill from CV" button. Work is described in experience entries, which keep their `technologies`. Stored `projects` data is ignored on read and dropped on the next save.
- Add `codeAccounts` to the profile: a list of `{provider, username}` (provider `GITHUB` first; the value set is open to GITLAB and others later), at most one account per provider.
- Add `openSourceProjects` to the profile: the repositories the user picked, stored as a snapshot (provider, repository full name, url, name, description, primary language, topics, stars, optional own `note`, order). Only picked repositories appear.
- Add `GET /api/profile/repositories?provider=GITHUB&username=<name>`: lists the public, non-fork, non-archived repositories of that account from the provider's public API, without token. Read-only, nothing is saved.
- UI: new section "Open Source Projects" in the profile page, with a GitHub account field, a "Load repositories" button showing a checklist, and a list of the picked projects the user can reorder, annotate and remove.
- Providers are behind one interface so GitLab and others can be added without changing the API shape or the stored format.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `profile`: remove Projects; add code accounts, open source projects, repository listing endpoint, limits and validation.
- `ui-profile`: remove Projects section and its Fill button; add the Open Source Projects section.
- `cv`: `section` no longer accepts `projects`.
- `llm`: the typed draft and section extraction no longer include projects; technologies per position stay.
- `ui-cv`: no change expected; checked in design.

## Impact

- Modules: `profile` (model, validator, provider interface, GitHub provider, listing service), `web` (new endpoint), `llm` and `cv` (removals), UI `profile` folder and `cvApi.ts`.
- Personal data: the GitHub username is personal data in the profile (stored only in the profile document, removed with the user, never logged). Repository snapshots are public data the user chose to keep.
- Per-user scoping: endpoint requires a valid token; it takes a public username as a lookup key, reads no user data and stores nothing. Saved accounts and projects stay scoped by `sub` like the rest of the profile.
- Outbound calls: the backend calls `api.github.com` unauthenticated (60 requests/hour per egress IP); no Claude cost.
- No database migration: the profile is a JSON document; `SCHEMA_VERSION` stays 1 since reading is lenient.

## Out of scope

GitLab or other providers (only the extension point), OAuth or tokens and private repositories, automatic refresh of snapshots, generating text about repositories with Claude, README import, contributions to repositories the user does not own, matching/scoring use of the new data.
