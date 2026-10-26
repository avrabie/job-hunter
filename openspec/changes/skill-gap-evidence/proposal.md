# Proposal

## Why

The skill analysis shipped in `letter-skill-gaps` sends the model only the names of the saved skills, so a skill the user
clearly has through their work (for example AI, shown in positions and projects but never typed into the skills list)
appears as a gap. The user cannot tell whether the lack is in the skills list or in the CV, and the letter then ignores
real experience. The analysis must look at the profile evidence, not only at the skill names.

## What Changes

- The analysis call also receives the profile evidence: each position's achievements and technologies, and each project's
  description and summary. Contact data, salary, preferences, work authorisation and the name are never sent.
- Each skill the job asks for gets a status: `LISTED` (a saved skill matches, decided in code as today), `EVIDENCED` (not in
  the skills list, but a position or project supports it, with a cited entry and a short quote), or `GAP`.
- The server checks every evidence claim against the profile text and turns a claim that fails into `GAP`. The model never
  decides `LISTED`.
- The letter workspace shows four groups: "Advised to include" (listed), "Evidenced in your CV and projects" (with the
  quote), "Gaps" (with note fields) and "Your other skills".
- **BREAKING (UI behavior, not data):** nothing is ticked by default any more. A "Match" button ticks every listed and
  evidenced skill; a "Clear" button unticks all; single ticks still work.
- Ticked evidenced skills reach the letter as references to the stored analysis. The server resolves them against the stored
  analysis and the profile, and the letter facts state them as skills evidenced by the cited position or project, using the
  user's own profile text.
- A stored analysis made before this change has no evidence: it reads as listed and gap only until the user analyses again.

Out of scope: scoring or matching of postings, adding an evidenced skill to the profile (even as a prompt), editing the
profile from this step, and sending any other profile data.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `motivation-letter`: the analysis input and result (evidence, status, server check), the letter selection and facts for
  evidenced skills, privacy of the extra profile text.
- `ui-applications`: four groups, no default ticks, Match and Clear, evidenced skills sent with the letter.

## Impact

- Modules: `llm` (`SkillExtractor` input and result, prompt), `applications` (matcher, evidence check, service, letter
  selection and facts, controller body), `profile` read through `ProfileService` only; UI `applications/`.
- Database: none. The stored `analysis` JSON gains an optional `evidence` per skill; old rows stay readable.
- Claude cost: the analysis request is larger (positions and projects instead of skill names); still one cached call per
  application with the same `skills-per-hour` limit. Letters gain a few lines of facts.
- Personal data: positions' achievements and technologies and project text now reach the model for the analysis (they
  already reach it for letters). Nothing of it is logged. Every query stays scoped by the JWT `sub`.
- Order: the specs of `letter-skill-gaps` must be archived first, because this change modifies requirements that only exist
  in that change until then.
