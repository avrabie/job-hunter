# Report: analyze-open-source-projects, group 1 (profile model and validation)

Worktree: `.claude/worktrees/agent-aaa38563b24cf8b7e` (branch `worktree-agent-aaa38563b24cf8b7e`). Nothing committed.

## Tasks finished

- **1.1** Model types in `Profile.kt`. Verify `./gradlew compileKotlin`: BUILD SUCCESSFUL (exit 0).
- **1.2** Validator rules. Verify `./gradlew test --tests "*ProfileValidator*"`: 51 tests, 0 failures, 0 errors (`ProfileValidatorTests.xml`). New cases cover every scenario of "Skill origin and sources", "Stored project analysis" and "Too many skills" (201 rejected, 200 accepted).
- **1.3** Sample in `full-profile.json` and legacy tests. Verify `./gradlew test --tests "*Profile*"`: BUILD SUCCESSFUL. Per class: ProfileApiTests 14, ProfileLoggingTests 1, ProfileSavedEventTests 4, ProfileSchemaTests 1, ProfileServiceTests 16, ProfileValidatorTests 51; all 0 failures, 0 skipped. The round-trip test (`a profile using every field round-trips`) passes with the new sample; I also added two asserts there (origin ANALYSIS, analysis summary).

Tasks ticked in `openspec/changes/analyze-open-source-projects/tasks.md`: 1.1, 1.2, 1.3. Groups 2 to 6 untouched.

## Not run

- `./gradlew build` (includes `ModularityTests`) was not run. It is group 4's verify and needs group 2 to 4. My worktree compiles on its own: `compileTestKotlin` ran as part of the `*Profile*` test task with no errors.
- The full `./gradlew test` was not run; only the profile-related classes.

## Files changed

- `backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt`
- `backend/src/main/kotlin/net/s4v3/backend/profile/internal/ProfileValidator.kt`
- `backend/src/test/kotlin/net/s4v3/backend/profile/internal/ProfileValidatorTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/ProfileServiceTests.kt`
- `backend/src/test/resources/profile/full-profile.json`
- `openspec/changes/analyze-open-source-projects/tasks.md` (ticks only)

## Contract and decisions

- Types are exactly the Contract names and signatures, in `profile` package, all in `Profile.kt`: `TechCategory`, `Technology(name, category, evidencePath, evidence)`, `ProjectAnalysis(summary, architecture, techStack, files, truncated, analyzedAt: Instant)`, `SkillOrigin`, `SkillSource(provider, fullName, url, evidence)`, `Skill(name, level = null, origin = MANUAL, sources = emptyList())`, `OpenSourceProject.analysis: ProjectAnalysis? = null`. No stand-ins were needed.
- Skill JSON in `full-profile.json` is the Contract's Skill JSON, verbatim, as the second skill after Kotlin and PostgreSQL. First project gets the analysis from the Contract: summary "A small Spring Boot service built with Gradle.", architecture "One application module with a REST controller.", one Spring Boot FRAMEWORK entry with evidencePath `build.gradle.kts`, files `["README.md","build.gradle.kts"]`, truncated false, analyzedAt `2026-10-24T12:00:00Z`.
- Validator choices the spec does not state; the lead should confirm:
  - `analysis.summary`: must not be blank (max 1500).
  - `analysis.architecture`: length check only (max 2500), so blank is accepted.
  - `techStack[i].name`: must not be blank (max 60).
  - `techStack[i].evidencePath` and `evidence`: length check only (max 300), so blank is accepted. The mapper in group 3 should still enforce its own rules.
  - `files[i]`: length check only (max 300); `files` count max 9.
  - `skills[i].sources[j].fullName`: must not be blank (max 200, the name limit). The spec does not give a limit here.
- `SKILLS_MAX` raised from 100 to 200.
- Legacy tests use versions 6 and 7 in the table; they are inserted directly as JSON, like the existing legacy tests.

## For the lead

- `ui/fixtures/full-profile.json` must be refreshed from the backend file in group 5. It now contains the new skill and the analysis.
- The Contract sample in `design.md` says `"level":null` in the Skill JSON; the file uses it verbatim, so `level` reads as null.
- In my worktree I used the Edit tool instead of a bash heredoc for some test edits, because the bash guard refused commands containing a `github` URL in a heredoc. This did not change any content.
