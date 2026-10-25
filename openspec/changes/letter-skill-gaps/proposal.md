# Proposal

## Why

When a user writes a motivation letter for a job, they choose skills by hand and have no view of what the posting asks for
that their profile lacks. A letter that ignores a visible gap reads as careless; one that names the gap and the nearest
real experience reads as honest and strong. The user needs the gap shown, and a place to say in their own words how they
would bridge it.

## What Changes

- New skill analysis per application: one model call reads the application's frozen job description and returns the skills
  it asks for, each marked required or nice-to-have. Matching against the user's saved skills is done by the server, not
  the model. The result is stored with the application and reused.
- Three lists for the user: skills to include (asked for and in the profile), gaps (asked for and not in the profile), and
  the user's other skills.
- For each gap the user may write a short note (what they can offer instead). Notes belong to the application and are not
  saved to the profile.
- The letter request carries the ticked skills and the notes. The letter may use a note as the user's own words and must
  never claim a gap skill.
- The analysis has its own hourly limit, separate from the letter limit.
- The letter workspace gets a "Skills for this job" step before Generate.

Out of scope: changing posting matching or scoring, saving notes into the profile, editing the profile from the step,
scoring candidates, analysing postings that are not an application, a gap badge in the postings list, DOCX or CV output.

## Capabilities

### New Capabilities

None. The analysis is part of the letter feature.

### Modified Capabilities

- `motivation-letter`: skill analysis of an application, gap notes, the extra letter input, a separate limit, privacy.
- `ui-applications`: the "Skills for this job" step in the letter workspace.

## Impact

- Modules: `applications` (analysis and notes endpoints, storage, letter input), `llm` (new `SkillExtractor`, letter prompt
  gets notes), `web` (endpoints, OpenAPI), UI `applications/`. `profile` is read only through its public API.
- Database: one table for the stored analysis and notes (Liquibase changeset), owner-scoped like `motivation_letter`.
- Claude cost: one extra Haiku-class call per application the first time, cached afterwards; refresh is user-triggered and
  limited per hour.
- Personal data: the model receives the job description and the skill names of the profile, nothing else. Notes are user
  text sent to the letter model only. Nothing of these is logged. Every query is scoped by the JWT `sub`.
