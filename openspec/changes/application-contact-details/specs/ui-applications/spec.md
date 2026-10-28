# Spec Delta

## ADDED Requirements

### Requirement: Company and contact section
`/applications/:id` SHALL have a "Company & contact" section with editable street, postal code, city, contact person, contact phone and contact email, saved with the same save action as the notes. An empty field is shown empty, never as "null". This section is shown for every stage.

#### Scenario: Edit and save
- **WHEN** the user enters a postal code and saves
- **THEN** the patch carries it, and reloading the page shows it

#### Scenario: Clear a value
- **WHEN** the user empties the contact phone and saves
- **THEN** the patch carries a blank value and the field is empty after reload

#### Scenario: Save refused
- **WHEN** the server answers 400 for an invalid email
- **THEN** the page shows the message and keeps what the user typed

### Requirement: RAV card shows the stored details
The RAV card of an applied application SHALL show the values the server returns, in the order of the RAV form, with a copy action per field. A missing value is shown as the text "null" with its copy action disabled, and the card SHALL point to the "Company & contact" section for filling it.

#### Scenario: Stored values
- **WHEN** the application has a contact person and no phone
- **THEN** the person is shown and can be copied, the phone is shown as "null" and cannot be copied

#### Scenario: Not applied
- **WHEN** the application is in any stage other than Applied
- **THEN** the RAV card is not shown
