# Spec Delta

## ADDED Requirements

### Requirement: Fill a section from the CV
The Experience, Education and Publications sections SHALL each have a "Fill from CV" button. It SHALL be enabled when the user has a master CV and otherwise disabled with a hint to choose one. It SHALL call `POST /api/cv/extract?section=<name>`, show a loading state in that section, and replace only that section's entries in the form with the returned ones, as unsaved changes, with a notice that nothing is saved until Save. When the section already has entries the page SHALL ask for confirmation first. Errors SHALL show the same fixed messages as the whole-profile button.

#### Scenario: Fill one section
- **WHEN** the user presses "Fill from CV" on Education
- **THEN** only the education entries change, the page says they are not saved, and no profile request is sent

#### Scenario: Existing entries
- **WHEN** the section already has entries and the user presses the button
- **THEN** a confirmation asks before they are replaced, and declining changes nothing

#### Scenario: No master
- **WHEN** the user has no master CV
- **THEN** the three buttons are disabled and a hint says to choose a master CV

#### Scenario: Error
- **WHEN** the server answers 422 or 429
- **THEN** a fixed message appears in that section and the entries are unchanged
