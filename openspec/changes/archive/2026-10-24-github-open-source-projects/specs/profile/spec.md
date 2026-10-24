## REMOVED Requirements

### Requirement: Projects
**Reason**: Work projects belong in experience entries; open-source work moves to its own section sourced from a code-hosting account.
**Migration**: Stored `projects` are ignored on read and dropped on the next save; clients stop sending `projects`.

## MODIFIED Requirements

### Requirement: Profile content
The profile SHALL be language-neutral and SHALL contain `location`, `workAuthorisation`, `languages`, `summary`, `experience`, `education`, `publications`, `codeAccounts`, `openSourceProjects`, `skills` and `preferences` (target roles, workload range, remote modes, cantons, minimum yearly salary in CHF). Experience and education entries carry `YYYY-MM` months; an entry without an end month is current. `publications`, `codeAccounts` and `openSourceProjects` are lists, empty by default, and every experience entry has a `technologies` list, empty by default.

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

#### Scenario: Legacy projects dropped on save
- **WHEN** a user whose stored document holds `projects` saves a profile
- **THEN** the new stored document has no `projects`

#### Scenario: Sending projects is rejected
- **WHEN** a request body contains a `projects` field
- **THEN** the response is 400 and nothing is stored

## ADDED Requirements

### Requirement: Code accounts
A code account SHALL have a `provider` (`GITHUB` for now) and a `username`. The username SHALL match the provider's rules (for `GITHUB`: 1 to 39 characters, letters, digits and single hyphens, not starting or ending with a hyphen). A profile SHALL have at most one account per provider. An unknown provider SHALL be rejected like an unknown enum value. Violations SHALL be rejected with the field path (for example `codeAccounts[0].username`) and a message that never repeats the submitted value.

#### Scenario: Valid account
- **WHEN** a profile with the account `{"provider":"GITHUB","username":"octocat"}` is saved and read back
- **THEN** the account is returned unchanged

#### Scenario: Bad username
- **WHEN** a GitHub username is `-bad-`, `a--b`, empty or longer than 39 characters
- **THEN** the response is 400 and `fields` contains `codeAccounts[0].username`

#### Scenario: Two accounts for one provider
- **WHEN** two accounts have the provider `GITHUB`
- **THEN** the response is 400 and `fields` contains `codeAccounts[1].provider`

#### Scenario: Unknown provider
- **WHEN** the provider is `BITBUCKET`
- **THEN** the response is 400 and nothing is stored

### Requirement: Open source projects
An open source project SHALL have `provider`, `fullName` (`owner/repo`), `name` and an absolute `https` `url`, and the optional fields `description` (at most 500 characters), `language` (at most 60), `topics` (at most 20, each at most 60), `stars` (not negative) and `note` (the user's own text, at most 2000 characters). `fullName` SHALL be unique per provider within a profile. A profile SHALL have at most 30 open source projects, kept in the order the user gave. Only projects the user saved SHALL appear; loading a repository list SHALL NOT change the profile. Violations SHALL be rejected with the field path and a message that never repeats the submitted value.

#### Scenario: Valid project
- **WHEN** a project with provider, full name, name, url, description, language, topics, stars and a note is saved and read back
- **THEN** it is returned unchanged and in the same order

#### Scenario: Required fields only
- **WHEN** a project has only provider, full name, name and url
- **THEN** it is accepted and the optional fields are null or empty

#### Scenario: Unsafe address
- **WHEN** a project's `url` is `javascript:alert(1)` or `http://github.com/x/y`
- **THEN** the response is 400 and `fields` contains `openSourceProjects[0].url`

#### Scenario: Duplicate repository
- **WHEN** two projects have provider `GITHUB` and the same `fullName`
- **THEN** the response is 400 and `fields` contains `openSourceProjects[1].fullName`

#### Scenario: Too many projects
- **WHEN** a profile has 31 open source projects
- **THEN** the response is 400 and nothing is stored

### Requirement: Repository listing
`GET /api/profile/repositories?provider=<P>&username=<U>` SHALL, for a caller with a valid token, return the public repositories of that account as `{"repositories":[{"provider","fullName","name","url","description","language","topics","stars","pushedAt"}]}`, excluding forks and archived repositories, newest push first, at most 100. It SHALL use the provider's public API without any user credential, SHALL NOT read or write the caller's profile, and SHALL validate `username` by the same rule as a code account before any outside call. Failures SHALL use fixed bodies: 400 `{"error":"invalid_username"}` or `{"error":"unknown_provider"}`, 404 `{"error":"account_not_found"}`, 429 `{"error":"provider_rate_limited"}`, 502 `{"error":"provider_unavailable"}`. Provider answers, usernames and repository text SHALL NOT be written to logs. The provider API address SHALL be fixed in configuration, not taken from the request.

#### Scenario: List repositories
- **WHEN** the provider answers with three public repositories, one a fork and one archived
- **THEN** the response is 200 and holds the one remaining repository with all listed fields

#### Scenario: Unknown account
- **WHEN** the provider answers 404 for the username
- **THEN** the response is 404 `account_not_found`

#### Scenario: Rate limited
- **WHEN** the provider answers 403 or 429 with an exhausted rate limit
- **THEN** the response is 429 `provider_rate_limited`

#### Scenario: Provider down
- **WHEN** the provider times out or answers 5xx
- **THEN** the response is 502 `provider_unavailable`

#### Scenario: Invalid input never reaches the provider
- **WHEN** the username is `../users` or the provider is `NOPE`
- **THEN** the response is 400 and no outside request is made

#### Scenario: Anonymous call
- **WHEN** the endpoint is called without a valid token
- **THEN** the response is 401

#### Scenario: Profile untouched
- **WHEN** a user lists repositories
- **THEN** their stored profile and its version are unchanged

### Requirement: Provider extension point
Repository listing SHALL be implemented per provider behind one internal contract (list public repositories of a username, with the failure kinds above), selected by the `provider` value. Adding a provider SHALL need only a new implementation and a new enum value, with no change to the endpoint shape, the stored project format or the validation structure.

#### Scenario: Provider selected by value
- **WHEN** a second provider implementation is registered in a test
- **THEN** requests with its `provider` value are served by it and `GITHUB` requests are unchanged
