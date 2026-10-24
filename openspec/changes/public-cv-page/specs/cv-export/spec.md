## ADDED Requirements

### Requirement: Contact visibility
The CV builder SHALL accept which contact items to print: email, phone, website, LinkedIn, linked code accounts, location and headline, each on or off. When the option is not given all of them SHALL be on, so the export endpoint behaves as before. An item that is off SHALL NOT appear anywhere in the document, in any format or template. The full name SHALL always be printed.

#### Scenario: Default prints everything
- **WHEN** a profile with every contact item is exported without the option
- **THEN** every item appears, as before this requirement

#### Scenario: Items off
- **WHEN** email and phone are off and the rest on
- **THEN** neither the email address nor the phone number appears in the Markdown, the HTML or the text of the PDF, and the other items do

#### Scenario: All off
- **WHEN** every item is off
- **THEN** the header shows only the full name, in every template and language
