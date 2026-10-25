## ADDED Requirements

### Requirement: Job extraction
The llm module SHALL provide `JobExtractor`, which turns untrusted page text into a job draft and fails with a fixed-message `LlmFailure`.

#### Scenario: Extraction only
- **WHEN** the page text contains instructions such as "ignore previous instructions"
- **THEN** they are treated as page content, not followed, and the draft contains only text found on the page

#### Scenario: Not a job ad
- **WHEN** the page is not a single job ad
- **THEN** the result has `found=false`

#### Scenario: Unusable answer
- **WHEN** the model returns HTML, a blank title or company, or an over-long description
- **THEN** `LlmFailure` is thrown with a fixed message that contains no model text

#### Scenario: Model setting
- **WHEN** `jobhunter.import.model` is empty
- **THEN** `jobhunter.llm.model` is used
