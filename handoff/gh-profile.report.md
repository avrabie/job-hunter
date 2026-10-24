# gh-profile report (tasks 1.1-1.4, change github-open-source-projects)

## Status: 1.1 to 1.4 done (not committed)

The first attempt stopped because the change directory was missing from this worktree. The coordinator copied it in
and asked me to resume. Nothing from the first attempt needed undoing.

## Tasks finished and verification

- **1.1** `Profile.kt`: `Project`, `ProjectType`, `ProjectLink`, `projects` removed. Added `CodeProvider { GITHUB }`,
  `CodeAccount`, `OpenSourceProject` (Contract fields), and `codeAccounts`, `openSourceProjects` after `publications`.
  Verify: `./gradlew compileKotlin` fails in exactly two files:
  - `llm/internal/DraftMapper.kt` (task 3.1, not 1.2/1.3/2.x as the task text says; expected per the lead's note)
  - `profile/internal/ProfileValidator.kt` (task 1.2; now fixed)
- **1.2** `ProfileValidator.kt`: project rules and constants removed. Added code account rules (provider appears once,
  error at `codeAccounts[i].provider`; username rule at `codeAccounts[i].username`) and open source project rules
  (fullName, name required; `https`-only url via a `schemes` parameter on `httpUrl`; description 500, language 60,
  topics max 20 with each at most 60, stars not negative, note 2000, list max 30, unique `fullName` per provider
  with error at `openSourceProjects[i].fullName`). Messages never echo values.
  `UsernameRules.kt` (new) has `internal fun isValidUsername(provider: CodeProvider, username: String)`: 1 to 39 chars,
  `[A-Za-z0-9]+(-[A-Za-z0-9]+)*`.
  `ProfileValidatorTests.kt` rewritten for the project cases. 32 tests, 0 failures.
- **1.3** `full-profile.json`: `projects` replaced by the Contract sample (codeAccounts + openSourceProjects).
  `ProfileServiceTests.kt`: project asserts removed. Added tests for a stored profile before open source projects existed
  (legacy `projects` ignored), legacy projects dropped on the next save, and unknown provider `BITBUCKET` rejected.
  `ProfileApiTests.kt`: added `sending projects is rejected and nothing is stored` (400, version stays 0) and
  `an unknown code provider is 400 and nothing is stored`.
  Results: ProfileApiTests 14, ProfileServiceTests 14, ProfileValidatorTests 32, plus logging/event/schema tests in the
  `*Profile*` filter, 0 failures.
- **1.4** Grep of `ProfileSaved`, `ProfileDirectory` and `matching` (and all of `src/main/kotlin`) for `projects`: no
  uses outside `llm/` and `Profile.kt`. `MAX_BODY_BYTES` untouched (256 * 1024).

## Important: how the tests were run

The worktree itself does not compile tests, because `llm/` still references Project types (task 3.1). I did not edit
`llm/` or `cv/`. Instead I ran the tests in a scratch copy:
`/private/tmp/claude-501/-Users-moldo-Documents-code-projects-job-hunter/6cf7a101-886a-41c1-9bf0-bcabbef38647/scratchpad/gh-backend`
- deleted `llm/DraftMapperTests.kt`, `llm/KoogCvReaderTests.kt`, `CvExtractApiTests.kt`
- removed the project parts from `DraftMapper.kt`
- in `KoogCvReader.kt`, replaced the PROJECTS branch with `error("scratch")`
The copy then ran `./gradlew test --tests '*ProfileValidator*' --tests '*Profile*'`: BUILD SUCCESSFUL, all listed
classes have 0 failures. Testcontainers ran (Docker is available). The copy is not part of the worktree.

## Files changed (worktree)

- backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt
- backend/src/main/kotlin/net/s4v3/backend/profile/internal/ProfileValidator.kt
- backend/src/main/kotlin/net/s4v3/backend/profile/internal/UsernameRules.kt (new)
- backend/src/test/kotlin/net/s4v3/backend/profile/internal/ProfileValidatorTests.kt
- backend/src/test/kotlin/net/s4v3/backend/ProfileServiceTests.kt
- backend/src/test/kotlin/net/s4v3/backend/ProfileApiTests.kt
- backend/src/test/resources/profile/full-profile.json
- openspec/changes/github-open-source-projects/tasks.md (ticks 1.1 to 1.4)
- handoff/gh-profile.report.md (this file)

## Not done / needs lead

- Task 3.1 (`llm/`, `cv/`) must land before the worktree compiles. Until then `./gradlew test` here fails at compile.
- Ticks for 1.1 and 1.2 and 1.3: 1.1 and 1.3 are ticked on the scratch-copy results above. If you want strict
  verification, rerun `*ProfileValidator*` and `*Profile*` after 3.1 is merged.
- Decision for the lead: the task text for 1.1 says compile errors belong to "1.2, 1.3 and 2.x", but `DraftMapper.kt`
  belongs to 3.1. The text should be corrected.
- Only the GITHUB provider exists, so the "second provider" and cross-provider duplicate cases are not tested yet.
  They belong with 2.1.
- Not committed.
