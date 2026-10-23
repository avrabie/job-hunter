# sections-backend report (change cv-sections, tasks 1.1 and 1.2)

## Summary
- Tasks 1.1 and 1.2 done and ticked. `cd backend && ./gradlew build` passes: 74 suites, 372 tests, 0 failures, 0 errors, 0 skipped.
- `llm` package green: KoogCvReaderTests 11 (7 old + 4 new), DraftMapperTests 9, KoogSpikeTests 4, LlmLoggingTests 1, KoogBoundaryTests 2.
- CvExtractApiTests 16 (11 old + 5 new); ModularityTests 2 green. Nothing committed; no UI, k8s or docs touched.

## Tasks finished
- 1.1: `ProfileSection` and `sectionFrom` added; section wrappers, mapper overloads, shared private request in `KoogCvReader`. Verify: `./gradlew test --tests 'net.s4v3.backend.llm.*' --tests 'net.s4v3.backend.KoogBoundaryTests'` BUILD SUCCESSFUL, counts above.
- 1.2: `section` request parameter in `CvExtractController`. Verify: `./gradlew build` BUILD SUCCESSFUL, counts above.

## Tasks not finished
- None of 1.1 or 1.2. Tasks 2.x, 3.1 untouched (not asked).

## Files changed
- backend/src/main/kotlin/net/s4v3/backend/llm/CvReader.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/KoogCvReader.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/DraftMapper.kt
- backend/src/main/kotlin/net/s4v3/backend/llm/internal/SectionDrafts.kt (new)
- backend/src/main/kotlin/net/s4v3/backend/cv/internal/CvExtractController.kt
- backend/src/test/kotlin/net/s4v3/backend/llm/KoogCvReaderTests.kt
- backend/src/test/kotlin/net/s4v3/backend/CvExtractApiTests.kt
- openspec/changes/cv-sections/tasks.md (ticked 1.1 and 1.2 only)
- handoff/sections-backend.report.md (this file)

## Deviations and doubts (please decide)
1. **`CvReader` is no longer a `fun interface`.** It now has two abstract methods, which a `fun interface` cannot have. Only the controller and the test fake implement it, so nothing else changed. Lambda-style construction of `CvReader` anywhere would break; none exists in the repo today.
2. **No-master message differs from tasks.md.** The controller's existing 409 text is "Choose a master CV in your files first.", and the existing test asserts it. tasks.md's Contract section says "Choose a master CV first.". I kept the code's text, as the brief asked ("keep that exact behaviour and message"). Task 2.1 quotes the contract text, so the lead should pick one and fix the other.
3. **Test fake edited during 1.1.** `CvExtractApiTests`' scripted reader must implement `sectionFrom` for the test sources to compile, so I added the override while doing 1.1. The section tests themselves are part of 1.2.
4. **Wrappers in a new file.** The three wrapper DTOs are in `llm/internal/SectionDrafts.kt`, not appended to `CvProfileDraft.kt`. They copy its annotations.
5. **Shared function shape.** The private `request<T>` takes the target type, the extra instruction, and a mapping lambda (`(T) -> Profile`). It is `inline reified` because `executeStructured<T>` needs the reified type. `profileFrom` passes a `null` instruction and its prompt text is unchanged.
6. **Empty section value.** `?section=` (empty) is treated as an unknown section and answers 400. Only a missing parameter means the whole profile. The brief did not cover this case.
7. **Prompt quality is untested against the real model.** The experience instruction ("every position, most recent first, every achievement, CV wording") is verified only through the fake executor, which checks that the text is sent. Nothing calls Anthropic, per the rules. Whether the model actually lists every achievement needs the lead's real-draft comparison.
8. **Section limits 413/422 not tested separately.** A section request goes through the same text checks as the whole profile, but only the cases in the four named scenarios have their own tests.
9. **Logging.** The section name is not logged; the existing log lines are unchanged. `LlmLoggingTests` and the CV content log test still pass.
10. **Build warnings.** The only compiler warnings are two deprecation warnings in `CvExtractApiTests` (`isUnprocessableEntity`, `isPayloadTooLarge`), which were already in the file. Not introduced by this change.
11. **Shell hiccups, no effect on results.** The sandbox refused some compound shell commands in this worktree, so I used the Read tool for some files. No code or git operations were affected.
