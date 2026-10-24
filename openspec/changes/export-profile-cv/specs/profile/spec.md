## MODIFIED Requirements

### Requirement: Profile content
The profile SHALL be language-neutral and SHALL contain `contact`, `location`, `workAuthorisation`, `languages`, `summary`, `experience`, `education`, `publications`, `codeAccounts`, `openSourceProjects`, `skills` and `preferences` (target roles, workload range, remote modes, cantons, minimum yearly salary in CHF). Experience and education entries carry `YYYY-MM` months; an entry without an end month is current. `publications`, `codeAccounts` and `openSourceProjects` are lists, empty by default, `contact` is always present with every field null by default, and every experience entry has a `technologies` list, empty by default.

#### Scenario: Round trip keeps every field
- **WHEN** a profile that uses every field is saved and read back
- **THEN** the returned content equals what was saved, with entries in the same order

#### Scenario: Current position
- **WHEN** an experience entry has no end month
- **THEN** it is accepted and returned with a null end month

#### Scenario: Profile saved before publications existed
- **WHEN** a stored profile document has no `publications` field
- **THEN** `GET /api/profile` returns it with an empty `publications` list

#### Scenario: Profile saved before projects and technologies existed
- **WHEN** a stored profile document has no `projects` field and its experience entries have no `technologies`
- **THEN** `GET /api/profile` returns it with empty `technologies` on every entry and no `projects` field

#### Scenario: Profile saved before open source projects existed
- **WHEN** a stored profile document has no `codeAccounts` and no `openSourceProjects`, and may still hold a legacy `projects` field
- **THEN** `GET /api/profile` returns it with empty `codeAccounts` and `openSourceProjects`, without a `projects` field

#### Scenario: Profile saved before contact existed
- **WHEN** a stored profile document has no `contact`
- **THEN** `GET /api/profile` returns it with a `contact` whose fields are all null

#### Scenario: Legacy projects dropped on save
- **WHEN** a user whose stored document holds `projects` saves a profile
- **THEN** the new stored document has no `projects`

#### Scenario: Sending projects is rejected
- **WHEN** a request body contains a `projects` field
- **THEN** the response is 400 and nothing is stored

## ADDED Requirements

### Requirement: Contact
The `contact` block SHALL have the optional fields `fullName` and `headline` (at most 200 characters each), `email` (at most 200 characters, one `@` with text on both sides and a dot in the domain part, no spaces), `phone` (at most 30 characters of digits, spaces, `+`, `-`, `(`, `)` and `/`), and `website` and `linkedin` (absolute `https` addresses of at most 500 characters). Nothing in it is required to save. Violations SHALL be rejected with the field path (for example `contact.email`) and a message that never repeats the submitted value. The contact data SHALL be stored only in the profile document and SHALL NOT be written to logs, exception messages or module events.

#### Scenario: Valid contact
- **WHEN** a profile with a full name, headline, email, phone, website and LinkedIn address is saved and read back
- **THEN** the contact block is returned unchanged

#### Scenario: Empty contact
- **WHEN** a profile is saved with every contact field null
- **THEN** it is accepted

#### Scenario: Bad values
- **WHEN** the email is `not an email`, the phone is `call me`, or the website is `http://example.org` or `javascript:alert(1)`
- **THEN** the response is 400 and `fields` contains `contact.email`, `contact.phone` or `contact.website`

#### Scenario: Too long
- **WHEN** the full name has 201 characters
- **THEN** the response is 400 and `fields` contains `contact.fullName`

#### Scenario: Nothing in logs
- **WHEN** a profile with a recognisable email is saved or rejected
- **THEN** the captured application log does not contain it
