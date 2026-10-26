# Tasks

## 1. Evidence check and result (applications, llm types)

- [x] 1.1 `AskedEvidence` and `SkillEvidenceInput` plain types in `llm`; `StoredAsked.evidence` optional with a default so old JSON reads. Verify: a unit test reads an old stored row without `evidence`.
- [x] 1.2 `EvidenceCheck`: entry exists, normalised quote occurs in the entry, skill or alias is a whole word in the entry, quote at most 200 characters without HTML. Verify: unit tests for pass, fabricated quote, quote present but skill absent, stale index, alias (LLM/AI only if in the table), HTML quote, whole-word edge (Go in "good").
- [x] 1.3 `SkillMatcher` and `DefaultSkillAnalysisService.view` return `include`, `evidenced`, `gaps`, `other`; `LISTED` first, then `EVIDENCED`, else `GAP`; `EvidencedSkill` carries source label, kind, index, quote. Verify: service tests incl. listed skill ignores evidence, profile edit turns evidenced into gap without a model call, old row has no evidenced, notes work only for gaps.

## 2. Model call (llm)

- [x] 2.1 `SkillExtractor.extract(job, evidenceInput)`; `SkillPrompts` adds `<profile_evidence>` (numbered positions and projects, tags stripped, caps from the design) and the rules: cite only an entry that names the skill, quote exactly, never claim the skill for the applicant. `KoogSkillExtractor` parses the optional evidence and drops malformed or over-long ones. Verify: recording-fake test: the request has description, skill names and numbered evidence and none of name, contact, salary, preferences or work authorisation; injection text in an entry stays quoted; malformed evidence is dropped, not a failure.
- [x] 2.2 Build `SkillEvidenceInput` in `applications` from `ProfileService` with the caps. Verify: a test with a long profile checks the caps and that excluded fields are absent.

## 3. Letter (applications, llm)

- [x] 3.1 `LetterSelection.evidenced`, request body `evidencedSkills`, validation against the derived `EVIDENCED` list (400 `selection_invalid`), stored with the version and read back for old versions without it. Verify: API tests for valid, gap name, unknown name, body-supplied quote ignored, old version readable.
- [x] 3.2 `Candidate.evidencedSkills`, prompt facts "Skill evidenced by ..." and the system rule; gap skills stay out. Verify: recording-fake tests: evidenced skill appears with source and quote from the profile and not among saved skills; injection in a quote stays data; no analysis behaves as before.
- [x] 3.3 Controller: response shape with `evidenced`, OpenAPI text, setting comments unchanged. Verify: API tests for GET / POST analyse / PUT notes bodies, user B 404, limiters unchanged, API-docs guard passes.
- [x] 3.4 Privacy: marker words in profile entries, quotes, skills and notes never reach logs or error bodies. Verify: log-capture test.

## 4. UI

- [x] 4.1 `skillsApi.ts` types (`evidenced`), `SkillsStep` four groups with quote text, no default ticks, "Match" and "Clear" buttons. Verify: component tests for nothing ticked, Match ticks advised and evidenced only, Match twice is idempotent, Clear, quotes rendered as text, repeat analysis clears ticks.
- [x] 4.2 Letter request sends ticked saved indexes plus `evidencedSkills` names and no quote or note text; Generate enabled when something is ticked. Verify: request-body test; skip path still works with the old picker.

## 5. Wrap-up

- [x] 5.1 Docs: `applications/README.md`, `llm/README.md` (new input, evidence rule, status derivation), PLAN.md line. Verify: docs mention the four statuses and what the model is sent.
- [x] 5.2 `./gradlew test integrationTest`, `ApplicationModules.verify()`, UI checks (vitest, tsc, oxlint, prettier), `openspec validate --all`.
- [ ] 5.3 Archive `letter-skill-gaps` first so the deltas apply; deploy backend and ui only after the owner says so; archive this change after the owner tests on prod.
