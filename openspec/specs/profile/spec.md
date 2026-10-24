# profile Specification

## Purpose

Defines the per-user master profile: the facts about a person that matching and document drafting may use, how it is stored and protected, and the API to read and replace it.

## Requirements

### Requirement: One master profile per user
The system SHALL store at most one master profile per user, keyed by the `sub` of the caller's token, and SHALL scope every read and write by that `sub`. No endpoint SHALL accept a user id.

#### Scenario: Empty profile for a new user
- **WHEN** a user who never saved a profile calls `GET /api/profile`
- **THEN** the response is 200 with `version` 0 and every list empty and every optional field null

#### Scenario: Save and read back
- **WHEN** a user saves a valid profile and then calls `GET /api/profile`
- **THEN** the response contains the saved content and `version` 1

#### Scenario: User A cannot reach user B's profile
- **WHEN** users A and B each saved a profile and A calls `GET /api/profile` and `PUT /api/profile`
- **THEN** A sees and changes only A's profile, and B's profile is unchanged

#### Scenario: Anonymous call
- **WHEN** `GET /api/profile` or `PUT /api/profile` is called without a valid token
- **THEN** the response is 401

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

### Requirement: Profile value sets
`workAuthorisation` SHALL be one of `SWISS_CITIZEN`, `PERMIT_C`, `PERMIT_B`, `PERMIT_L`, `PERMIT_G`, `NEEDS_SPONSORSHIP` or null. A language SHALL have a two-letter lowercase code and a level `A1` to `C2` or `NATIVE`. A skill level SHALL be `BEGINNER`, `INTERMEDIATE`, `ADVANCED` or `EXPERT`. Remote modes SHALL be `ONSITE`, `HYBRID` or `REMOTE`. Cantons SHALL be two-letter codes.

#### Scenario: Permit values
- **WHEN** each listed `workAuthorisation` value is saved
- **THEN** it is accepted, and any other value is rejected with 400

### Requirement: Validation and limits
The system SHALL reject a profile that breaks a rule with status 400 and a body `{"error":"validation_failed","fields":{"<path>":"<message>"}}`. Rules: required text not blank; start month not after end month; workload between 10 and 100 with minimum not above maximum; salary not negative; unknown enum values and unknown fields rejected.

#### Scenario: Blank required field
- **WHEN** an experience entry has a blank title
- **THEN** the response is 400 and `fields` contains `experience[0].title`

#### Scenario: Dates out of order
- **WHEN** an entry starts after it ends
- **THEN** the response is 400 and names the end field

#### Scenario: Unknown value
- **WHEN** `workAuthorisation` is `"CITIZEN"` or an unknown top-level field is sent
- **THEN** the response is 400 and nothing is stored

### Requirement: Size limits
Name-like fields SHALL be at most 200 characters, the summary 4000 and each achievement 600. A profile SHALL have at most 30 experience entries, 20 achievements per entry, 20 education entries, 200 skills, 10 languages, 10 target roles and 30 cantons.

#### Scenario: Too many entries
- **WHEN** a profile has 31 experience entries
- **THEN** the response is 400 and nothing is stored

#### Scenario: Too long text
- **WHEN** a summary has 4001 characters
- **THEN** the response is 400 and nothing is stored

#### Scenario: Too many skills
- **WHEN** a profile has 201 skills
- **THEN** the response is 400 and nothing is stored

### Requirement: Safe error messages and body size
Validation messages SHALL describe the rule and SHALL NOT contain the submitted value. A request body larger than 256 KB SHALL be rejected with 413 before it is parsed into a profile.

#### Scenario: Value not echoed
- **WHEN** a too-long summary made of a recognisable marker text is rejected
- **THEN** the response body does not contain the marker text

#### Scenario: Oversized body
- **WHEN** the request body is larger than 256 KB
- **THEN** the response is 413

#### Scenario: Body between the old and the new limit
- **WHEN** a valid profile whose request body is 200 KB is saved
- **THEN** it is accepted and read back unchanged

### Requirement: Whole-document replace with a version check
The request and response body SHALL be `{"version": n, "profile": {...}}`. `PUT /api/profile` SHALL replace the caller's whole profile when `version` equals the stored version (0 when nothing is stored), store it with the version increased by one, and return the saved profile. Otherwise it SHALL return 409 with `{"error":"version_conflict"}` and change nothing.

#### Scenario: First save
- **WHEN** a user with no stored profile sends a valid profile with `version` 0
- **THEN** the response is 200 with `version` 1

#### Scenario: Update
- **WHEN** the stored version is 3 and the client sends `version` 3
- **THEN** the response is 200 with `version` 4

#### Scenario: Stale save
- **WHEN** the stored version is 4 and the client sends `version` 3
- **THEN** the response is 409 and the stored profile is unchanged

#### Scenario: Two saves race
- **WHEN** two requests with the same `version` arrive at the same time
- **THEN** exactly one succeeds and the other gets 409

### Requirement: Profile data is personal data
The profile SHALL be stored only in the `profile` table, which SHALL reference `app_user(sub)` with `ON DELETE CASCADE`, so deleting a user deletes the profile. The system SHALL NOT write any profile content to logs, exception messages or metrics, and SHALL NOT include profile content in any module event.

#### Scenario: Deleting the user removes the profile
- **WHEN** a user row is deleted
- **THEN** the user's profile row is gone

#### Scenario: Nothing in logs
- **WHEN** a profile is saved, rejected for validation, or rejected for a version conflict
- **THEN** the captured application log contains none of the submitted text

### Requirement: ProfileSaved event
The system SHALL publish a `ProfileSaved` event after a profile is replaced successfully. The publisher is `profile`. The payload is `userId` (the token's `sub`) and `version` (the new stored version). It carries no profile content. The consumer is `matching`. The event SHALL NOT be published when the save is rejected (validation failure or version conflict).

#### Scenario: Event on save
- **WHEN** a user saves a valid profile
- **THEN** exactly one `ProfileSaved` event with that user's id and the new version is published and recorded by the event publication registry

#### Scenario: No event on rejection
- **WHEN** a save fails validation or conflicts on the version
- **THEN** no `ProfileSaved` event is published

#### Scenario: No content in the event
- **WHEN** the event is published
- **THEN** its payload holds only the user id and the version

### Requirement: Stored profiles readable by other modules
The `profile` module SHALL expose, through its public API only, a page-by-page listing of all stored profiles with their owners' ids, for modules that act for every user (such as `matching`). It SHALL return only profiles that were saved, in a stable order, and SHALL NOT be reachable over HTTP.

#### Scenario: Listing
- **WHEN** another module lists stored profiles and two users have saved one
- **THEN** it receives those two profiles with their owner ids, and no user who never saved a profile

#### Scenario: Not an endpoint
- **WHEN** a client requests any HTTP path for this listing
- **THEN** no such path exists

### Requirement: Publications
A publication SHALL have a required `title` and the optional fields `venue` (journal, conference or publisher), `year`, `authors` (free text) and `url`. `year` SHALL be a whole number from 1900 to the current year plus one. `url` SHALL be an `http` or `https` address. Text limits: `title` and `venue` at most 200 characters, `authors` at most 500, `url` at most 500. A profile SHALL have at most 100 publications. Violations SHALL be rejected like other profile errors, with the field path (for example `publications[0].year`) and a message that never repeats the submitted value.

#### Scenario: Valid publication
- **WHEN** a profile with a publication that has a title, venue, year 2021, authors and an https link is saved and read back
- **THEN** the publication is returned unchanged

#### Scenario: Title only
- **WHEN** a publication has only a title
- **THEN** it is accepted and the other fields are null

#### Scenario: Blank title
- **WHEN** a publication has a blank title
- **THEN** the response is 400 and `fields` contains `publications[0].title`

#### Scenario: Year out of range
- **WHEN** a publication has year 1850, or a year more than one above the current year
- **THEN** the response is 400 and `fields` contains `publications[0].year`

#### Scenario: Unsafe link
- **WHEN** a publication's `url` is `javascript:alert(1)` or a relative path
- **THEN** the response is 400 and `fields` contains `publications[0].url`

#### Scenario: Too many publications
- **WHEN** a profile has 101 publications
- **THEN** the response is 400 and nothing is stored

### Requirement: Technologies
An experience entry and a project SHALL each have a `technologies` list of short tags. A tag SHALL not be blank and SHALL be at most 60 characters, and a list SHALL have at most 30 tags. The server SHALL accept any casing and SHALL NOT change the tags; tidying (trimming and removing duplicates that differ only in case) is the client's and the CV reader's job.

#### Scenario: Tags kept as sent
- **WHEN** an experience entry with technologies `["Kotlin", "Spring Boot"]` is saved and read back
- **THEN** the same two tags are returned in the same order

#### Scenario: Blank or long tag
- **WHEN** a tag is blank, or has 61 characters
- **THEN** the response is 400 and `fields` contains the path of that tag, for example `experience[0].technologies[1]`

#### Scenario: Too many tags
- **WHEN** an entry has 31 technologies
- **THEN** the response is 400 and nothing is stored

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

### Requirement: Skill origin and sources
A skill SHALL have a `name`, an optional `level`, an `origin` (`MANUAL` by default, or `ANALYSIS`) and a `sources` list (empty by default). A source SHALL have a `provider`, a `fullName` (`owner/repo`), an absolute `https` `url` (at most 500 characters) and an `evidence` text (not blank, at most 300 characters). A skill SHALL have at most 20 sources. A stored profile without `origin` and `sources` SHALL read as `MANUAL` with no sources. Violations SHALL be rejected with the field path (for example `skills[3].sources[0].url`) and a message that never repeats the submitted value.

#### Scenario: Round trip
- **WHEN** a skill with origin `ANALYSIS` and one source is saved and read back
- **THEN** it is returned unchanged

#### Scenario: Old skills
- **WHEN** a stored skill has only a name and a level
- **THEN** it reads with origin `MANUAL` and an empty `sources` list

#### Scenario: Bad source
- **WHEN** a source has the url `javascript:alert(1)` or a blank evidence
- **THEN** the response is 400 and `fields` contains the path of that field

#### Scenario: Too many sources
- **WHEN** a skill has 21 sources
- **THEN** the response is 400 and nothing is stored

### Requirement: Stored project analysis
An open source project MAY have an `analysis` (null by default) with `summary` (at most 1500 characters), `architecture` (at most 2500), `techStack` (at most 20 entries, each `name` at most 60, `category` one of `LANGUAGE`, `FRAMEWORK`, `LIBRARY`, `DATABASE`, `TOOL`, `PLATFORM`, `OTHER`, `evidencePath` at most 300, `evidence` at most 300), `files` (at most 9 paths of at most 300 characters), `truncated` and `analyzedAt`. A stored profile without it SHALL read with `analysis` null. Violations SHALL be rejected like other profile errors.

#### Scenario: Round trip
- **WHEN** a project with a full analysis is saved and read back
- **THEN** the analysis is returned unchanged

#### Scenario: Old projects
- **WHEN** a stored project has no `analysis`
- **THEN** it reads with `analysis` null

#### Scenario: Too many technologies
- **WHEN** an analysis lists 21 technologies, or a summary has 1501 characters
- **THEN** the response is 400 and nothing is stored
