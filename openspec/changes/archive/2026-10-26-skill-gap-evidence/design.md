# Design

## Context

`letter-skill-gaps` (deployed as backend 0.19.5) stores the model's list of asked-for skills in `letter_skill_analysis.analysis`
(JSON array of `StoredAsked(name, importance, related)`) and derives include / gaps / other on every read with `SkillMatcher`
against the saved skill names. `SkillExtractor.extract(job, savedSkillNames)` sends only skill names. The letter request carries
`skills` as saved-profile indexes (`LetterSelection`) and `LetterRequest.gapNotes`. Evidence for AI-like skills lives in
`Experience.achievements` / `technologies` and `OpenSourceProject.description` / `analysis`, which the analysis never sees.
See proposal.md for why.

## Goals / Non-Goals

**Goals:**
- Let the model point at profile text that supports a skill missing from the skills list.
- Keep the guarantee that the letter never states a skill the profile does not support.
- No default ticks; a Match button as the user's explicit step.

**Non-Goals:**
- No profile writes, no scoring, no embeddings, no new table.

## Decisions

1. **Evidence is a claim, checked by code.** The model answers per skill with an optional
   `evidence: {kind: EXPERIENCE|PROJECT, index, quote}`. `EvidenceCheck` (in `applications/internal`, in the style of
   `ContactGrounding`) passes it only if the entry exists at that index in the current profile, the normalised quote (lowercase,
   collapsed whitespace) occurs in the entry's text, and the entry's text contains the skill name or an alias as a whole word
   (whole-word rule and aliases reused from `SkillMatcher`). Alternative considered: trust the model's verdict; rejected because a
   wrong "you have it" puts an invented skill into a letter, which the project forbids.
2. **Status is derived on read, not stored.** The row keeps what the model said (asked skills with its optional evidence);
   `LISTED / EVIDENCED / GAP` is computed against the current profile each time, so deleting or editing a position takes effect
   without a model call and indexes that drift fail the check instead of pointing at the wrong entry. `LISTED` is checked first,
   so the model never decides it. Old rows have no `evidence` field and read as listed or gap.
3. **What the model sees.** `SkillExtractor.extract(job, profile: SkillEvidenceInput)` where the input is plain types:
   saved skill names, positions (index, title, employer, achievements, technologies) and projects (index, name, description,
   summary). Built in `applications` from `ProfileService`; no name, contact, preferences, salary or work authorisation. Each entry
   is numbered and sits between `<profile_evidence>` tags as data; tags are stripped with `PromptText.strip`. Size: each
   achievement cut at 300 characters, at most 12 positions and 10 projects, total cap 15 000 characters, so cost stays bounded.
   Model, timeout and limiter are unchanged.
4. **Result type.** `AskedSkill` gains `evidence: AskedEvidence?` (kind, index, quote, at most 200 characters, HTML refused).
   `SkillsState.Analysed` becomes `include`, `evidenced`, `gaps`, `other`; `EvidencedSkill(name, importance, source: label,
   kind, index, quote)`, where `label` is "<title> at <employer>" or the project name, built from the profile.
5. **Letter carries names only.** `LetterSelection` gets `evidenced: List<String>` (stored in the version's selection JSON,
   default empty, so old versions still read). `DefaultLetterService` resolves each name through the same derivation and answers
   `selection_invalid` if it is not currently `EVIDENCED`; the request body cannot carry quotes. `Candidate` gets
   `evidencedSkills: List<EvidencedSkill(name, source, quote)>`; the prompt lists them inside `<candidate_facts>` as "Skill
   evidenced by <source>: <name> - \"<quote>\"" (tags stripped), and the system prompt allows stating such a skill only as shown.
   The gap-skill rule from `letter-skill-gaps` stays.
6. **UI.** `SkillsStep` shows four groups. Ticks live in the workspace state: saved skill indexes and evidenced names. "Match"
   sets them to all of `include` and `evidenced` (a set union, so it is idempotent); "Clear" empties both. After analyse or
   notes save nothing is ticked. The old default of ticking advised skills is removed. Evidenced quotes render as text.
7. **API.** Response of GET / POST analyse / PUT notes gains `evidenced: [{name, importance, kind, index, source, quote}]`;
   `include` and `gaps` keep their shape. The letter POST body gains `evidencedSkills: [name]`. Documented in OpenAPI.
8. **No migration.** Only the JSON in an existing column changes, with a default for the missing field.

## Risks / Trade-offs

- [Model cites a real entry for a loosely related skill] -> whole-word check of the skill or its alias in the entry, plus the
  user ticks Match or individual skills knowingly and the letter says only what the quote shows.
- [Honest synonyms fail the check (for example "LLM" for "AI")] -> they stay gaps with related hints; the alias table can grow;
  acceptable because a false gap costs less than a false claim.
- [Larger prompt, higher cost] -> caps in decision 3; still one cached call per application.
- [Prompt injection through a profile entry or the description] -> numbered data between tags, tags stripped, fixed instructions,
  fake-model test that records the request.
- [Indexes shift after a profile edit] -> status is re-derived; a stale index fails the check and becomes a gap.
- [Module boundary] -> `applications` reads the profile through `ProfileService`; `llm` receives plain types, not profile classes.

## Migration Plan

1. Archive `letter-skill-gaps` so its requirements exist in the main specs, then this change's deltas apply.
2. Deploy backend and UI together (the UI needs `evidenced` in the response). Old stored analyses keep working; the first
   re-analysis adds evidence. Rollback: previous images; extra JSON fields and the extra selection key are ignored by older code.
