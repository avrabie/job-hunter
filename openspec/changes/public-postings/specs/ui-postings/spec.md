## ADDED Requirements

### Requirement: Latest postings on the landing page
The landing page SHALL show up to 6 of the latest public postings (title, company, city, how long ago it was first seen), each with a "Sign in to read more" action, loaded from `GET /api/public/postings` without a token. No description SHALL be shown. If the request fails, is limited or returns nothing, the section SHALL be absent and the rest of the page SHALL be unaffected. Postings texts SHALL be rendered as text, never as HTML.

#### Scenario: Latest postings
- **WHEN** a visitor opens the landing page and the endpoint returns 6 postings
- **THEN** the 6 postings are listed with company, city and age, and a sign-in action each

#### Scenario: Endpoint failing
- **WHEN** the request fails or answers 429
- **THEN** the landing page shows no postings section and no error

#### Scenario: Empty
- **WHEN** the endpoint returns no postings
- **THEN** the postings section is absent

#### Scenario: Markup in a title
- **WHEN** a title contains `<b>x</b>`
- **THEN** it is shown as the literal text
