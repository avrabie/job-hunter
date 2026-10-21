# Proposal

## Why

The platform runs end to end (sign-in, gateway, `backend`, UI) but `backend` has no endpoint and the UI shows "The backend has no endpoints yet." Every later feature (matching, CV and letter drafting) reads the user's master profile as its only source of facts (`PLAN.md` section 7), so the profile is on the critical path of phases 3 and 4. It is also the smallest real slice that exercises the whole stack: a per-user table, a scoped REST API, validation, and a UI form behind the token.

## What Changes

- Add the `profile` module to `backend`: one language-neutral master profile per user, stored as a validated JSON document in a new `profile` table keyed by the JWT `sub`.
- Expose `GET /api/profile` and `PUT /api/profile`. `GET` returns the caller's profile, or an empty profile at version 0 when none is saved. `PUT` replaces the whole profile and uses a version number to reject a stale save with 409.
- The profile holds: contact location, work-authorisation status for Switzerland, spoken languages with level, a short summary, work experience, education, skills, and job preferences (target roles, workload range, remote modes, cantons, minimum salary in CHF).
- Server-side validation with fixed size limits and field-level error messages that never repeat the submitted value.
- A profile page in the UI (`/profile`): load, edit all sections, save, show field errors and the stale-version conflict. The signed-in shell links to it.
- Deploy `backend` and `ui` to `s4v3.net` and verify in a browser.

**Explicitly out of scope** (later changes): importing a CV and extracting the profile with Claude (needs the `llm` module); a sample motivation letter and tone settings; account export and deletion (need every module to exist; only the cascade from `app_user` is prepared); the `ProfileChanged` event (no consumer yet); an OpenAPI document and a generated client (the UI uses hand-written types until more endpoints exist); a photo upload; German and English UI text; normalised tables for experience and skills; Google sign-in.

## Capabilities

### New Capabilities
- `profile`: what the master profile contains, how it is stored per user, the API to read and replace it, validation and size limits, the version check, and the privacy rules.
- `ui-profile`: the profile page, how it loads and saves, and how it shows errors and conflicts.

### Modified Capabilities
- None. The `accounts` rules (current user, per-user isolation, no credentials in logs) apply unchanged and are referenced by the new specs.

## Impact

- **Modules touched:** `profile` (new content: public `ProfileService` and the profile types; internal repository and validation), `web` (internal REST controller that calls `ProfileService`), `accounts` (read only: `CurrentUser`). `ApplicationModules.verify()` must keep passing; `web` depends on `profile`, never the reverse.
- **Database:** new Liquibase changeset `004-profile` creating table `profile` with a foreign key to `app_user(sub)` and `ON DELETE CASCADE`.
- **Dependencies:** none new in `backend` (JDBC, Jackson and validation are already there). None new in `ui`.
- **Personal data:** the profile is personal data under nDSG/GDPR (work history, permit status, location). It lives only in the `profile` table, is never logged (not in application logs, error messages or validation responses) and is deleted with the user through the cascade. Real profile content stays out of git and out of test fixtures (tests use invented data).
- **Per-user scoping:** every read and write is scoped by the `sub` of the token; there is no endpoint that takes a user id. A scenario proves user A cannot read or overwrite user B's profile.
- **Claude cost:** none.
- **External systems:** none.
