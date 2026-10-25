# llm

Purpose: everything that talks to a language model (Claude through Koog). The only module allowed to import `ai.koog` (ArchUnit rule).

## Public API (this package)
- `CvReader`: `profileFrom(text)` and `sectionFrom(text, ProfileSection)` return a typed `Profile` draft.
- `ProjectAnalyst.analyze(RepositoryDigest)` returns a checked `ProjectAnalysis`.
- `LetterWriter.write(LetterRequest)` returns Markdown letter text (request types `JobText`, `Candidate`, `PreviousLetter`, `ParagraphComment`, `LetterLanguage`); one plain-text request, answer cleaned (code fence stripped, empty, HTML or over 6 000 chars refused).
- `LlmFailure(kind)` with `NOT_CONFIGURED` or `FAILED`: the only error; fixed message, never prompt or answer text.
- `ProfileSection`: `EXPERIENCE`, `EDUCATION`, `PUBLICATIONS`, `CONTACT` (contact values are kept only if the CV text contains them: `ContactGrounding`).

## Internal (`internal/`)
- `KoogCvReader`, `KoogProjectAnalyst`: one structured request each (manual JSON mode), timeout, repair parser on `Haiku_4_5`.
- `AnthropicExecutorSource` / `ExecutorSource`: seam replaced by a fake in tests.
- `CvProfileDraft`, `SectionDrafts`, `ProjectAnalysisDraft`: what the model fills in.
- `DraftMapper`, `AnalysisMapper`: draft to model types; the analysis keeps only technologies whose cited path is in the digest.
- `KoogLetterWriter`, `LetterPrompts` (system prompt and user template; the description is data between `<job_description>` tags, cut at 20 000 chars), `LettersSettings`.
- `LlmSettings` (its `toString` hides the key).

## Events
- None.

## Tables
- None.

## Settings
- `jobhunter.llm.api-key` (env `ANTHROPIC_API_KEY`), `model`, `repairs`, `timeout`; `jobhunter.letters.model` (empty = `jobhunter.llm.model`), `jobhunter.letters.timeout` (60 s). Koog logging is switched off in `application.yaml`.

## Calls
- `profile` (types only). HTTP out to Anthropic.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/llm/` and root)
- `KoogCvReaderTests`, `KoogProjectAnalystTests` (with `FakePromptExecutor`), `KoogLetterWriterTests`, `DraftMapperTests`, `AnalysisMapperTests`, `KoogSpikeTests`, `LlmLoggingTests`.
- `KoogBoundaryTests` (root). Fakes: `ai/koog/fake/FakeKoogClient.kt`.

Read on 2026-10-24 from: llm/*.kt, llm/internal/KoogCvReader.kt, KoogProjectAnalyst.kt, AnalysisMapper.kt, LlmSettings.kt, test file names.
