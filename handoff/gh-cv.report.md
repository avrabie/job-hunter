# Report: task 3.1 (CV reader cleanup, projects removed)

## Status

- Task 3.1: code done, tests green in a scratch copy. **Not ticked** in tasks.md: the verify command does not pass in this worktree as it stands, because group 1 has not landed (see "Blocked by group 1").

## Verify results

Run in a scratch copy of `backend/` (not the worktree), with `ProfileValidator` projects block removed, `ProfileValidatorTests.kt` deleted and one `projects` assertion removed from `ProfileServiceTests.kt`. Those are group 1 files and were changed in the copy only.

`./gradlew --offline test --tests '*Draft*' --tests '*KoogCvReader*' --tests '*CvExtract*'`: BUILD SUCCESSFUL
- `CvExtractApiTests`: 17 tests, 0 failures
- `DraftMapperTests`: 11 tests, 0 failures
- `KoogCvReaderTests`: 15 tests, 0 failures

## Blocked by group 1

In the worktree, `./gradlew --offline compileKotlin` fails only in `profile/internal/ProfileValidator.kt` (20 errors, all `projects` references). Test compile is also blocked by `ProfileValidatorTests.kt` (Project/ProjectLink/ProjectType) and `ProfileServiceTests.kt:102` (`stored.profile.projects`). These are task 1.2 and 1.3 files, which I did not touch.

## Files changed (worktree)

Main:
- `backend/src/main/kotlin/net/s4v3/backend/llm/internal/CvProfileDraft.kt`: removed `projects` field, `ProjectDraft`, `ProjectLinkDraft`.
- `backend/src/main/kotlin/net/s4v3/backend/llm/internal/SectionDrafts.kt`: removed `ProjectsSection`.
- `backend/src/main/kotlin/net/s4v3/backend/llm/internal/DraftMapper.kt`: removed project mapping, `ProjectsSection` overload, `RESULTS_MAX`, project imports. `tags()` kept for experience technologies.
- `backend/src/main/kotlin/net/s4v3/backend/llm/internal/KoogCvReader.kt`: removed PROJECTS branch and `PROJECTS_INSTRUCTION`.
- `backend/src/main/kotlin/net/s4v3/backend/llm/CvReader.kt`: `ProfileSection` is now `EXPERIENCE, EDUCATION, PUBLICATIONS`.
- `backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvExtractController.kt`: OpenAPI text lists experience, education, publications only.
- `backend/src/main/kotlin/net/s4v3/backend/profile/Profile.kt`: minimal edit per the brief. Removed `Project`, `ProjectType`, `ProjectLink`, the `projects` field. Group 1 owns this file; expect a conflict at merge.

Tests:
- `backend/src/test/kotlin/net/s4v3/backend/llm/DraftMapperTests.kt`: project tests removed (they test removed code). Replaced by a sentence-tag test on experience technologies, so the tag rule stays covered.
- `backend/src/test/kotlin/net/s4v3/backend/llm/KoogCvReaderTests.kt`: projects-section test replaced by two tests: a stray `projects` list is ignored in a whole-profile answer and in a section answer.
- `backend/src/test/kotlin/net/s4v3/backend/CvExtractApiTests.kt`: the projects-section test now asserts 400, "That section cannot be read.", and the model is not called. Project imports removed.

Not changed: `tasks.md` (3.1 stays `[ ]`), `web/`, `ui/`, `ProfileValidator`.

## Notes for the lead

- Koog's structured parse ignores unknown keys: the stray-`projects` tests pass without any parser change.
- `openspec/changes/github-open-source-projects/` is untracked in this worktree (copied in by you). I did not commit anything.
- The 3.1 verify should be re-run in the worktree after group 1 merges. Then tick `3.1`.
- Remaining `project` hits in `backend/src/main/kotlin/.../llm` and `cv`: none. Other modules (`ProfileSaved`, `ProfileDirectory`, `matching`) were not checked; that is task 1.4.
