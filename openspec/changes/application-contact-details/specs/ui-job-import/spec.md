# Spec Delta

## ADDED Requirements

### Requirement: Company address and contact in the import review
The review step of "Add job from URL" SHALL show street, postal code, city, contact person, contact phone and contact email as editable fields, filled with what the preview found and empty where it found nothing. The user's edits are sent with the confirm. The step SHALL say that these values come from the ad and must be checked.

#### Scenario: Found values
- **WHEN** the preview returns a street, postal code and city
- **THEN** the three fields are filled and the contact fields are empty

#### Scenario: Edited and confirmed
- **WHEN** the user types a contact email and confirms
- **THEN** the confirm request carries that email, and the new application shows it

#### Scenario: Invalid edit
- **WHEN** the server answers 400 `invalid_edit`
- **THEN** the review stays open, shows the message and keeps what the user typed

### Requirement: Paste step after an extraction failure
The paste text area SHALL also be offered after `extraction_failed`, and that code's message SHALL tell the user to paste the job description instead of trying again later.

#### Scenario: Extraction failed
- **WHEN** a preview answers `extraction_failed`
- **THEN** the message says to paste the job description and the paste text area is shown
