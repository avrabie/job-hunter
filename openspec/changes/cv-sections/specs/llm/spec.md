# Spec Delta

## ADDED Requirements

### Requirement: Section extraction
The module SHALL offer a typed extraction of one profile section (experience, education or publications) from CV text. The prompt SHALL ask for every entry of that section the CV states, with all stated details (for experience every achievement, not a summary), and SHALL keep the rules of the whole-profile extraction: only stated facts, no guessing, CV text as quoted data, no tools. The same value checks as for the whole profile apply: an invalid optional value becomes null and an entry that lacks a required value is left out.

#### Scenario: Only the requested section
- **WHEN** the model returns a valid answer for the experience section
- **THEN** the result is a profile whose experience entries come from the answer and whose other sections are empty

#### Scenario: Complete entries
- **WHEN** the answer has a position with six achievements
- **THEN** all six are in the result in order

#### Scenario: Bad values
- **WHEN** an entry has an invalid start month
- **THEN** that entry is left out and the others are kept

#### Scenario: Unparseable answer
- **WHEN** the answer cannot be parsed after the repair attempts
- **THEN** the call fails with the same fixed error as the whole-profile extraction
