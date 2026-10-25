# Design

## Context

Letters live in `applications` (`LetterService`, table `motivation_letter`, owner-scoped) and call the `llm` module through
`LetterWriter` with plain request types. `LetterRequest` already carries a `Candidate` with chosen `skills`. The application
holds a frozen job description. Skills in the profile are `Skill(name, level, origin, sources)`. See proposal.md for why.

## Goals / Non-Goals

**Goals:**
- Show asked-for skills split into have / gap / other, with the match decided by code.
- Let the user write a note per gap that reaches the letter as their own words.
- Keep cost low: one call, cached, user-triggered refresh.

**Non-Goals:**
- No embeddings or fuzzy matching; no change to prefilter scoring; no profile writes.

## Decisions

1. **`SkillExtractor` in `llm`, like `LetterWriter`.** Public interface with plain types
   (`JobText`, saved skill names -> `AskedSkill(name, importance, related)`), Koog only inside `llm/internal`. Same manual
   JSON structure mode as `KoogCvReader` (the native grammar mode is refused for large schemas). The job description is
   quoted data. The model gets skill names only, never levels, sources or other profile parts. Model: `jobhunter.letters.model`
   (default Haiku); it only extracts a list.
2. **Matching in code, in `applications`.** A small `SkillMatcher`: normalise (lowercase, strip punctuation and spaces) and a
   fixed alias table (Postgres/PostgreSQL, K8s/Kubernetes, JS/JavaScript, ...). Chosen over asking the model because a wrong
   "you have it" would put an invented skill into a letter, which the project forbids. Cost: some honest synonyms land in
   gaps; the user sees them with "related" hints and can write a note or tick a skill by hand.
3. **Related hints are filtered to real names.** The model may name up to 3 of the user's own skills per gap; any name not
   exactly in the saved list is dropped. They are hints only and never move a skill into `include`.
4. **Storage: one table `letter_skill_analysis`** (Liquibase `013`): `application_id` (PK, FK, cascade delete), `owner_sub`,
   `analysis` jsonb (asked skills with importance and related), `notes` jsonb, `created_at`. Owner column and index like
   `motivation_letter`. The description is frozen, so the application id is the only cache key. Alternative: columns on the
   application row; rejected to keep `applications` rows small and the cascade simple.
5. **Match is computed on read, not stored.** The stored analysis holds what the model said; `include/gaps/other` are
   derived from the current profile each time, so profile edits show without a new model call. Notes are kept by skill
   name; notes whose skill is no longer a gap are ignored on read and dropped on the next save or refresh.
6. **Letter input.** `LetterRequest` gets `gapNotes: List<GapNote(skill, text)>`. The prompt puts them in a separate quoted
   block "written by the candidate" and states gap skills are not candidate skills. `GenerateLetterCommand` is unchanged:
   the server reads the notes of the application itself, so the request body cannot inject notes. The skill choice stays
   indexes of the saved profile; the UI sends the ticked ones.
7. **Own limiter.** A second fixed-window limiter of the same kind as the letters limiter, setting
   `jobhunter.letters.skills-per-hour` (20). Reading and saving notes are not counted.
8. **Endpoints** (on the application, owner-scoped): `GET /api/applications/{id}/skills` (the stored result, or `{analysed:false}` when there is none), `POST .../skills/analyse` (model call, replaces), `PUT .../skills/notes`.
   Fixed error codes `skills_not_configured`, `skills_failed`; 429 uses the existing shape. Documented in OpenAPI; the
   guard test that every endpoint is documented must pass.
9. **UI.** `SkillsStep` inside `LetterWorkspace`, `skillsApi.ts` beside `lettersApi.ts`. State: ticked indexes, notes.
   The old free skill picker is shown only when there is no analysis.

## Risks / Trade-offs

- [Model lists a skill under a different name than the profile] -> alias table plus related hints plus manual ticking.
- [User writes a claim in a note that is untrue] -> notes are the user's own text, the check notice already says to verify.
- [Prompt injection through the description or a note] -> quoted data, fixed instructions, fake-model test that records the request.
- [Extra cost] -> one cheap call per application, cached, limited per hour.
- [Module boundary] -> `applications` reads skills through the `profile` public API only; `llm` gets names, not profile types, for this call.

## Migration Plan

Liquibase adds a table; no data to migrate; applications without an analysis behave as today. Rollback: drop the table and
the endpoints; letters are unaffected.
