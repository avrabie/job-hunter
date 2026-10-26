## ADDED Requirements

### Requirement: Fill the contact section from the CV
The Contact section SHALL have a "Fill from CV" button, enabled when the user has a master CV and otherwise disabled with the same hint as the other sections. It SHALL call `POST /api/cv/extract?section=contact`, show a loading state in that section and put the returned values into the form as unsaved changes, with a notice that nothing is saved until Save. A field that is empty in the form SHALL be filled from a returned value. A field that already holds a different value SHALL be replaced only after the user confirms, and the confirmation SHALL name the affected fields but not show their values; declining SHALL leave those fields as they are. When no contact value is returned the page SHALL say that nothing was found. Errors SHALL show the same fixed messages as the other sections.

#### Scenario: Empty form filled
- **WHEN** the contact fields are empty and the user presses "Fill from CV" in Contact
- **THEN** the returned name, email, phone, website and LinkedIn appear in the fields, the page says they are not saved, and no profile request is sent

#### Scenario: Existing values kept unless confirmed
- **WHEN** the full name already holds a different value than the CV and the user presses the button
- **THEN** a confirmation names "Full name", and declining keeps the existing name while still filling the empty fields

#### Scenario: Nothing found
- **WHEN** the server returns an empty contact
- **THEN** a message says that no contact details were found and the form is unchanged

#### Scenario: No master
- **WHEN** the user has no master CV
- **THEN** the Contact button is disabled and a hint says to choose a master CV

#### Scenario: Error
- **WHEN** the server answers 422 or 429
- **THEN** a fixed message appears in the Contact section and the fields are unchanged
