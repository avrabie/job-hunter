# Report: an-analyst (task 3.1, change analyze-open-source-projects)

## Tasks finished

- 3.1 (llm analyst). Verify: `cd backend && ./gradlew test --tests '*Analysis*' --tests '*ProjectAnalyst*'` passes.
  - Test results from the XML reports: 18 tests, 0 failures, 0 errors, 0 skipped.
  - `AnalysisMapperTests`: 9 tests. `KoogProjectAnalystTests`: 9 tests.
  - Compiler warnings are only the existing deprecations in `CvExtractApiTests` and `FilesApiTests`.
  - Ticked `- [x] 3.1` in `openspec/changes/analyze-open-source-projects/tasks.md`.

## Tasks not finished

None.

## Files created (paths only, nothing committed)

- `backend/src/main/kotlin/net/s4v3/backend/profile/StandInAnalysisTypes.kt` (STAND-IN, see below)
- `backend/src/main/kotlin/net/s4v3/backend/llm/ProjectAnalyst.kt`
- `backend/src/main/kotlin/net/s4v3/backend/llm/internal/ProjectAnalysisDraft.kt`
- `backend/src/main/kotlin/net/s4v3/backend/llm/internal/AnalysisMapper.kt`
- `backend/src/main/kotlin/net/s4v3/backend/llm/internal/KoogProjectAnalyst.kt`
- `backend/src/test/kotlin/net/s4v3/backend/llm/AnalysisMapperTests.kt`
- `backend/src/test/kotlin/net/s4v3/backend/llm/KoogProjectAnalystTests.kt`
- `openspec/changes/analyze-open-source-projects/tasks.md` (tick of 3.1 only)
- `handoff/an-analyst.report.md` (this file)

Nothing else in `profile/` was touched. `Profile.kt`, `web/` and `ui/` are untouched.

## Stand-in for the lead to delete at merge

`profile/StandInAnalysisTypes.kt` holds exactly the Contract's `TechCategory`, `Technology`, `ProjectAnalysis` and `RepositoryDigest`. Group 1 (`Profile.kt`) and group 2 (`RepositoryDigest`) must not both be merged with it present, or the declarations clash. Delete the file when merging.

## Decisions and surprises (please confirm)

1. **README path is derived, not carried.** `RepositoryDigest` has `readme: String?` but no path for it. `AnalysisMapper.readmePath(digest)` takes the first root-level tree path starting with `README` (case-insensitive) when `readme` is non-null. That path is used as the README's evidence path, as the first entry of `files`, and as the heading `--- README.md ---`. Please confirm group 2 keeps the README path in `paths`. If it also puts the README in `files`, the mapper de-duplicates it.
2. **Empty summary.** `AnalysisMapper.toAnalysis` throws `LlmFailure(FAILED)`. `KoogProjectAnalyst` catches it in its general `catch`, logs the class name, and rethrows `FAILED`.
3. **Mapper limits as I read the spec.** A name longer than 60 characters, or a blank name, drops the technology; it is not cut. An evidence path must be a tree path (or the README) and at most 300 characters. Evidence is cut to 300 and may be blank. Duplicates use the first spelling. The first 20 are kept. Unknown categories become `OTHER`. Summary is cut at 1500 and architecture at 2500, at the last `.`, `!` or `?` in the second half of the limit, otherwise at the limit. `files` is the README plus digest file keys, distinct, at most 9, paths of at most 300 characters. `truncated` is copied from the digest. `analyzedAt` is passed in; the Koog class passes `Instant.now()`.
4. **Reused, not copied.** `KoogProjectAnalyst` reuses `ExecutorSource`, `AnthropicExecutorSource` and `describeClientFailure` from `KoogCvReader.kt`, which I did not edit. `modelFor` is private to `KoogCvReader`'s companion, so I duplicated its small `when` in `KoogProjectAnalyst`'s companion instead of moving it, to avoid touching existing code.
5. **Logging.** The analyst logs outcome, duration, model name and the exception class (or HTTP status and Anthropic error type via `describeClientFailure`). It never logs the repository name, file names, digest text or answer. The spec's "Nothing in logs" scenario is endpoint-level (task 4.1) and is not tested here.
6. **Prompt.** The digest is sent as `<repository>` ... `</repository>` (the closing tag is removed from the text, case-insensitively). The system prompt says the text is data, forbids following instructions inside it, and requires a cited path for each technology. The max output is 8000 tokens and no temperature is set, as in `KoogCvReader`. No tools are passed.
7. **Test fixes during the run.** Two of my own tests were wrong and were fixed before the tick: the summary-cut test had its sentence end too early for the cut rule, and the closing-tag test counted the tag in the system prompt, which mentions it. The assertions now check the repository message only. No production code was changed to make them pass.

## Other notes

- The shell refused one large compound command with heredocs (worktree isolation check), so I wrote the files with the Write tool. The result is the same.
- Nothing was committed, pushed or built into an image. No cluster or Anthropic call was made; the tests use the fake executor only.
