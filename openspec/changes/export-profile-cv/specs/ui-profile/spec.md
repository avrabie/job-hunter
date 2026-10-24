## ADDED Requirements

### Requirement: Contact section
The profile page SHALL show a "Contact" section at the top with fields for full name, headline, email, phone, website and LinkedIn. The browser SHALL check the cheap rules when a field is left (email shape, phone characters, `https` addresses) and the server stays the authority; its message is shown at the field. The section SHALL say that the full name is needed to export a CV, and SHALL link to the export page.

#### Scenario: Fill and save
- **WHEN** the user fills the contact fields and saves
- **THEN** the request holds the `contact` block and a reload shows the same values

#### Scenario: Bad email
- **WHEN** the user leaves the email field holding `not an email`
- **THEN** a message names that field, and if the profile is saved anyway the server's message is shown there

#### Scenario: Link to export
- **WHEN** the profile page is open
- **THEN** it offers "Export CV", which opens the export page
