## ADDED Requirements

### Requirement: Letter writer
The llm module SHALL offer `LetterWriter`, which takes a job (title, company, description), the candidate (full name and the chosen experience, projects and skills), the language, optional instructions and optionally the previous text with the user's comments, and returns Markdown text. Callers never see Koog, prompts or model answers. It SHALL fail only with `LlmFailure` (fixed messages), use the configured model with the existing timeout, and apply the output checks of the `motivation-letter` capability. The system prompt and the prompt template SHALL live in one internal file each, covered by tests that pin their key rules.

#### Scenario: Fake model
- **WHEN** a fake model records the request
- **THEN** the request has the data delimiters around the description, contains only the given candidate parts, and the instructions text is placed outside the delimiters

#### Scenario: Unusable answer
- **WHEN** the model returns an empty answer or HTML
- **THEN** `LlmFailure(FAILED)` is thrown with the fixed message
