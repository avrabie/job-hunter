# exp-backend report: experience-projects, backend tasks 1.1 to 2.2

## Tasks finished

- 1.1 Model: `Experience.technologies`, `ProjectType`, `Project`, `ProjectLink`, `Profile.projects`. `./gradlew compileKotlin` passes.
- 1.2 Validation: tag rules and project rules in `ProfileValidator`. `ProfileValidatorTests` 25 tests, 0 failures (12 new). `HOBBY` rejection test added in `ProfileServiceTests` (next to `CITIZEN`). `ProfileServiceTests` 12 tests, 0 failures.
- 1.3 Body limit 256 KB, renamed 413 test (now sends 300 KB), new `Body between the old and the new limit` test (17 positions with 20 achievements each, asserted body size 190 to 210 KB). Contract sample added to `full-profile.json`. New test: stored document without `projects` and `technologies` reads with defaults. `./gradlew test --tests '*Profile*'`: ProfileApiTests 12, ProfileServiceTests 12, ProfileSavedEventTests 4, ProfileSchemaTests 1, ProfileLoggingTests 1, ProfileValidatorTests 25; 0 failures.
- 2.1 Draft types `ProjectDraft`, `ProjectLinkDraft`, `ExperienceDraft.technologies`, `CvProfileDraft.projects`; `DraftMapper` mapping. `DraftMapperTests` 13 tests (4 new), 0 failures.
- 2.2 `ProfileSection.PROJECTS`, `ProjectsSection`, `DraftMapper.toProfile(ProjectsSection)`, `PROJECTS_INSTRUCTION`, `when` branch in `KoogCvReader.sectionFrom`, experience instruction asks for technologies. OpenAPI texts of `CvExtractController` list `projects`. Tests: `KoogCvReaderTests` 14 (1 new, projects only), `CvExtractApiTests` 17 (1 new, `projects` answers 200 and reader receives PROJECTS; `hobbies` still 400, existing test). `KoogBoundaryTests` 2 and `ModularityTests` 2 pass.

## Full build

`cd backend && ./gradlew build`: BUILD SUCCESSFUL. Whole test run: 74 suites, 395 tests, 0 failures, 0 errors, 0 skipped. Gradle output shows two existing deprecation warnings in `CvExtractApiTests.kt` (lines 190 and 200, `isUnprocessableEntity` / `isPayloadTooLarge`), not touched.

## Tasks not finished

None of the assigned tasks. Tasks 3.x (UI) and 4.1 (docs) were out of scope and not started.

## Files changed

- backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt
- backend/src/main/kotlin/net/s4v3/backend/profile/ProfileService.kt
- backend/src/main/kotlin/net/s4v3/backend/profile/internal/ProfileValidator.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/CvReader.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/CvProfileDraft.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/DraftMapper.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/SectionDrafts.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/KoogCvReader.kt
- backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvExtractController.kt
- backend/src/test/kotlin/net/s4v3/backend/profile/internal/ProfileValidatorTests.kt
- backend/src/test/kotlin/net/s4v3/backend/ProfileServiceTests.kt
- backend/src/test/kotlin/net/s4v3/backend/ProfileApiTests.kt
- backend/src/test/kotlin/net/s4v3/backend/llm/DraftMapperTests.kt
- backend/src/test/kotlin/net/s4v3/backend/llm/KoogCvReaderTests.kt
- backend/src/test/kotlin/net/s4v3/backend/CvExtractApiTests.kt
- backend/src/test/resources/profile/full-profile.json
- openspec/changes/experience-projects/tasks.md (checkboxes 1.1, 1.2, 1.3, 2.1, 2.2 ticked)
- handoff/exp-backend.report.md (this file)

Koog request mode, token limit (16000) and temperature handling in `KoogCvReader` are unchanged; only the new `when` branch and instruction texts were added.

## Deviations and doubts

1. Tidying in the draft: spec says tags are trimmed, blanks dropped, case-insensitive duplicates removed, at most 30. I did not cut tags longer than 60 characters, results longer than 600, or more than 20 results in the draft. A draft with such a value is accepted by the mapper and then rejected by the server on Save (400 with the field path). Decide whether the mapper should clip these.
2. A project type that is null or blank also becomes `PERSONAL` (the spec only names "unknown"). Same choice as for "hobby".
3. `ProjectDraft.name` and `ProjectLinkDraft.label`/`url` follow the existing pattern: `name` is a non-null `String` like `PublicationDraft.title`. A missing `name` key in the model's JSON would fail the whole answer, not just that project. Blank names are left out as specified.
4. A link is kept when it starts with `http://` or `https://` (the existing `link()` helper of publications). `https://` with no host passes the mapper; the server rejects it on Save.
5. The mapper does not check that a project's start is not after its end; the server rejects it on Save. The spec does not say to drop it.
6. The OpenAPI text for `section` (controller) and its 400 text were changed; the existing test that checks the 400 message still uses the fixed `That section cannot be read.` text (unchanged).
7. Validation messages never echo values (existing rule kept); my new messages are the same rule-only texts, and the new "never echoes" test covers project fields.
8. Test name `Body between the old and the new limit` is written exactly as in the spec scenario, with a capital letter, as the task text asked.

## Decisions needed from the lead

- Whether the draft mapper should clip over-long tags and results (point 1), or leave the rejection to Save as now.
- Whether `ProjectDraft.name` should default to an empty string so that a missing name drops only that project (point 3).
