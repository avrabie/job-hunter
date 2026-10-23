# Proposal

## Why

The single "Fill the profile from my CV" button drafts everything at once from one model answer, which tends to summarise long careers. Users also want to refresh one part of the profile (for example Experience after a new job) without touching the rest. A request for one section can ask the model to be complete for just that section.

## What Changes

- `POST /api/cv/extract` accepts an optional `section` (`experience`, `education` or `publications`) and then asks the model only for that section, returning a profile with just that section filled.
- The profile page gets a "Fill from CV" button on the Experience, Education and Publications sections. It reads the master CV, asks before replacing a section that already has entries, and puts the result in the form as unsaved changes.
- The whole-profile button stays as it is.

## Capabilities

### New Capabilities

### Modified Capabilities
- `cv`: the extraction endpoint can extract a single section.
- `llm`: a section-specific typed extraction.
- `ui-profile`: per-section fill buttons.

## Impact

- Backend: `llm` (`CvReader` gets a second method, section DTOs and prompts), `cv` controller (query parameter).
- UI: `ui/src/profile/` (section headers, API call), `ui/src/cv/cvApi.ts`.
- Depends on `profile-publications` (the publications section) and `file-cabinet` (the master CV): implement them first.
- Backend and UI redeploy.
