## ADDED Requirements

### Requirement: Preview an import from a URL
The system SHALL accept a job URL from a signed-in user, fetch the page, extract title, company, location, description and language with the LLM, store the result as a draft owned by that user, and return it without creating a posting or application.

#### Scenario: Readable job page
- **WHEN** a user previews `https://jobs.example.com/ada-engineer` and the page is a job ad
- **THEN** the response has a `draftId`, title, company, location, a plain-text description and a language, and no posting or application exists yet

#### Scenario: Page is not a job ad
- **WHEN** the page is a blog post
- **THEN** the response is 422 `not_a_job` and no draft is stored

#### Scenario: Page needs JavaScript or login
- **WHEN** the readable text is under 300 characters
- **THEN** the response is 422 `unreadable` and the message tells the user to paste the description

#### Scenario: Description invented by the model
- **WHEN** fewer than 80 % of the extracted description's words occur in the page text
- **THEN** the response is 502 `extraction_failed` and no draft is stored

### Requirement: Paste fallback
The system SHALL accept `{url, text}` and then extract from the text without any outbound request.

#### Scenario: Pasted text
- **WHEN** a user previews with a URL and 2 000 characters of text
- **THEN** no HTTP request leaves the server and a draft is returned

### Requirement: Safe fetching
The system SHALL only fetch https URLs on port 443 whose host resolves exclusively to public addresses, connect to the validated address, follow at most 3 manually validated redirects, and stop at 10 seconds or 1 MB.

#### Scenario: Internal addresses
- **WHEN** the URL host resolves to loopback, a private range, link-local (including the cloud metadata address), unique-local or an IPv4-mapped form of those
- **THEN** the response is 400 `blocked_address` and no connection is made

#### Scenario: IP literal, http, other port or userinfo
- **WHEN** the URL is `http://…`, `https://203.0.113.5/…`, `https://host:8443/…` or `https://user@host/…`
- **THEN** the response is 400 `invalid_url`

#### Scenario: Redirect to an internal address
- **WHEN** a public host redirects to a host resolving to a private address
- **THEN** the response is 400 `blocked_address`

#### Scenario: DNS changes between check and connect
- **WHEN** the host first resolves to a public address and a second lookup would return a private one
- **THEN** the connection goes to the first, validated address only

#### Scenario: Too large or wrong type
- **WHEN** the body exceeds 1 MB, or the content type is not HTML
- **THEN** the response is 422 `too_large` or `not_html`

#### Scenario: Sites the project does not scrape
- **WHEN** the host is `linkedin.com`, `indeed.com` or a subdomain
- **THEN** the response is 400 `unsupported_site` and nothing is fetched

### Requirement: Confirm an import
The system SHALL turn the caller's own unexpired draft into an INTERESTED application owned by that user, with the frozen description and `source = IMPORT`, applying any edits to title, company, location and description within their length limits. It SHALL NOT create or change any shared posting.

#### Scenario: New job
- **WHEN** a user confirms a draft whose URL they do not yet track
- **THEN** the response is 201 with `created=true`, the application is INTERESTED with the draft's text, and the shared postings table is unchanged

#### Scenario: Already tracked by this user
- **WHEN** the user already has an imported application with the same canonical URL
- **THEN** `created=false` and the existing application is returned

#### Scenario: Canonical URL
- **WHEN** two URLs differ only by `utm_source`, a fragment or a trailing slash
- **THEN** they count as the same job for that user

#### Scenario: Not visible to others
- **WHEN** user B lists applications, reads the application by id, or lists postings
- **THEN** user A's imported job appears nowhere and the id answers 404

#### Scenario: Another user's draft
- **WHEN** user B confirms a draft id created by user A
- **THEN** the response is 404 `draft_not_found` and nothing is created

#### Scenario: Expired draft
- **WHEN** a draft is older than 1 hour
- **THEN** confirm returns 404 `draft_not_found`

#### Scenario: Invalid edits
- **WHEN** an edit is blank, over its length limit, or contains markup
- **THEN** the response is 400 `invalid_edit` and nothing is created

#### Scenario: Letters work on imported jobs
- **WHEN** the user generates a letter for an imported application
- **THEN** it uses the frozen copy exactly like for a posting-based application

### Requirement: Limits
The system SHALL allow each user at most 20 previews per hour and 10 confirms per day.

#### Scenario: Preview limit
- **WHEN** a user makes a 21st preview within an hour
- **THEN** the response is 429 `rate_limited` and no fetch or model call is made

### Requirement: Privacy
The system SHALL never log URLs, page text, extracted text or model output, and SHALL remove a user's drafts when that user's data is removed.

#### Scenario: Logs
- **WHEN** an import fails or succeeds
- **THEN** the log lines contain only the error code, draft id and duration

#### Scenario: Data removal
- **WHEN** `removeUserData(sub)` is called
- **THEN** that user's drafts are deleted

### Requirement: Concurrency
The system SHALL allow at most 2 concurrent previews per user and 8 overall, answering 429 `rate_limited` beyond that, and SHALL limit the request body size of the import endpoints.

#### Scenario: Parallel previews
- **WHEN** a user starts a third preview while two are running
- **THEN** the response is 429 `rate_limited` and no fetch is made
