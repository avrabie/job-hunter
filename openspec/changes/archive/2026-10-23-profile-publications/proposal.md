# Proposal

## Why

Researchers and engineers list publications on their CV, but the profile has no place for them, so the CV import drops them and later CV drafts could not use them. Experience and education already exist; publications are the missing section.

## What Changes

- The profile gets a `publications` list (title, venue, year, authors, link). It is optional and empty by default, so profiles saved before this change still load.
- The profile validation covers publications (required title, year range, http(s) link, size limits).
- The CV-to-profile draft (llm module) can return publications, with the same "drop a bad value, never guess" rules as the other sections.
- The profile page gets a Publications section with the same add, remove and reorder behaviour as experience and education.

## Capabilities

### New Capabilities

### Modified Capabilities
- `profile`: the content gains `publications`, with rules and limits.
- `llm`: the typed draft includes publications.
- `ui-profile`: the page edits publications.

## Impact

- Backend: `profile` (model, validator, JSON), `llm.internal` (draft DTO, mapper, prompt text), shared test fixture `backend/src/test/resources/profile/full-profile.json`.
- UI: `ui/src/profile/` (types, rules, form, fixture, tests).
- No database migration: the profile is a JSON document and a missing `publications` reads as an empty list.
- Backend and UI redeploy; `auth` untouched.
