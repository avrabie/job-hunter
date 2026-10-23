# Proposal

## Why

A position in the profile says where and when someone worked but not with what, and there is no place for work that is not a job: university assignments, personal projects and open-source work, with their results. For a CV tailored to a job description both matter: the technologies show relevance, and projects fill the gaps of a short career. Today they either get lost when a CV is read or have nowhere to live.

## What Changes

- Each experience entry gets `technologies` (a list of tags such as `Kotlin`, `Spring Boot`).
- The profile gets a `projects` list. A project has a `name`, a `type` (`ASSIGNMENT`, `PERSONAL`, `OPEN_SOURCE` or `WORK`), an optional `context` (course, university, employer), an optional period, a `description`, `technologies`, `results` (short outcome lines), `links` (label plus `http`/`https` address) and a `highlight` flag for the ones to prefer when tailoring a CV.
- Both are optional and empty by default, so stored profiles keep loading and no database migration is needed.
- The CV reader fills technologies and projects, and the `section=projects` extraction and a "Fill from CV" button on Projects exist like for the other sections.
- The profile page gets a tag input for technologies (trimmed, de-duplicated without regard to case, with suggestions taken from the user's skills and tags already used) and a Projects section.

## Capabilities

### New Capabilities

### Modified Capabilities
- `profile`: experience technologies and the projects list, with rules and limits.
- `llm`: the typed draft and the section extraction include technologies and projects.
- `cv`: `section=projects` is an accepted section.
- `ui-profile`: the tag input, the Projects section, and the fill button on Projects.

## Impact

- Backend: `profile` (model, validator), `llm.internal` (draft types, mapper, section wrapper and instruction), `llm` (`ProfileSection.PROJECTS`), `cv` controller (accepts `projects`), shared test fixture `backend/src/test/resources/profile/full-profile.json`.
- UI: `ui/src/profile/` (types, rules, tag input, form, page), `ui/src/cv/cvApi.ts`, the shared fixture.
- No migration; backend and UI redeploy; `auth` untouched.
- Out of scope, later: importing projects from GitHub, using technologies in the matching score, highlight on positions, a read-only profile preview.
