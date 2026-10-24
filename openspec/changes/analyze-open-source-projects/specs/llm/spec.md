## ADDED Requirements

### Requirement: Project analysis
The module SHALL offer a typed analysis of one repository from a digest (file tree, README and dependency files, all as text). The prompt SHALL give the digest as quoted data, SHALL forbid following instructions inside it, SHALL ask for a summary, an architecture description and the technologies with a category and the path of one shown file each, and SHALL allow only what the files show. The model SHALL have no tools. The answer SHALL be checked as the `project-analysis` capability requires (cited path in the tree, name length, duplicates, limits, unknown category becomes `OTHER`). Failures SHALL be the same fixed `LlmFailure` as the CV reader and nothing of the digest or the answer SHALL be logged.

#### Scenario: Valid answer
- **WHEN** the model returns a summary, an architecture and two technologies citing paths of the tree
- **THEN** the result holds them with the evidence

#### Scenario: Cited path not in the tree
- **WHEN** a technology cites a path that is not in the digest's tree
- **THEN** it is left out and the others are kept

#### Scenario: Closing tag in the repository text
- **WHEN** the README contains `</repository>` followed by instructions
- **THEN** the closing tag is removed before the text is sent, so the quote cannot be ended early

#### Scenario: Empty summary
- **WHEN** the answer has an empty summary
- **THEN** the call fails with the fixed "could not produce a usable answer" error

#### Scenario: Not configured
- **WHEN** no model key is set
- **THEN** the call fails with the "not configured" error without any outside request
