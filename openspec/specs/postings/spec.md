# postings Specification

## Purpose

Defines the shared, normalized store of job postings: how a source hands a posting over, how duplicates are recognised, how the posting language is detected, and which event announces a new posting. All users see the same postings; nothing here is per user.

## Requirements

### Requirement: Normalized posting record
The system SHALL store each posting with: title, company name, description text, location (city and canton when known), workload range in percent, remote mode (`onsite`, `hybrid`, `remote`, or unknown), salary range in CHF when given, posting language, the source id and the source's own posting id, the posting URL, the time it was first seen and the time it was last seen.

#### Scenario: Minimal posting
- **WHEN** a source submits a posting with only title, company, description, source id, source posting id and URL
- **THEN** it is stored, and location, workload, remote mode and salary are recorded as unknown

#### Scenario: Workload range
- **WHEN** a posting states "80-100%"
- **THEN** the stored workload minimum is 80 and the maximum is 100

#### Scenario: Required fields missing
- **WHEN** a submitted posting has an empty title, company, source id, source posting id or URL
- **THEN** it is rejected, nothing is stored, and the rejection names the missing field

### Requirement: Raw payload retention
The system SHALL keep the raw payload the source delivered for every posting, unchanged, next to the normalized record, so the posting can be re-parsed later without crawling again.

#### Scenario: Payload stored
- **WHEN** a posting is ingested with a raw payload
- **THEN** the stored raw payload equals the submitted one byte for byte

#### Scenario: Payload replaced on update
- **WHEN** a re-crawl delivers a changed payload for an existing posting
- **THEN** the stored payload is the newest one

### Requirement: Idempotent ingest per source
Ingesting a posting SHALL be idempotent per (source id, source posting id). The result SHALL be reported as `created`, `updated` or `unchanged`, and the last-seen time SHALL be refreshed in every case.

#### Scenario: First sight
- **WHEN** a posting with a new (source id, source posting id) is ingested
- **THEN** one posting row exists afterwards and the result is `created`

#### Scenario: Same posting again
- **WHEN** the same posting is ingested twice with identical content
- **THEN** one posting row exists, the second result is `unchanged`, and the last-seen time is newer than after the first call

#### Scenario: Content changed
- **WHEN** a posting that exists is ingested again with a different description
- **THEN** the stored description is the new one, the first-seen time is unchanged, and the result is `updated`

#### Scenario: Concurrent ingest
- **WHEN** two ingests of the same new (source id, source posting id) run at the same time
- **THEN** exactly one posting row exists afterwards and neither call fails

### Requirement: Cross-source dedupe
The system SHALL treat postings from different sources as the same posting when their normalized company name, normalized title and city match, and SHALL link them to one canonical posting rather than storing a second visible copy. Matching SHALL ignore case, surrounding whitespace, punctuation, and gender markers such as "(m/w/d)".

#### Scenario: Same ad on two boards
- **WHEN** source A and source B each deliver "Senior Kotlin Developer (m/w/d)" at "Acme AG" in Zurich
- **THEN** one canonical posting exists with two source references

#### Scenario: Different city is a different posting
- **WHEN** two sources deliver the same title and company but different cities
- **THEN** two canonical postings exist

#### Scenario: Unknown city does not merge
- **WHEN** a posting has no city
- **THEN** it is deduplicated only against postings from the same source id

#### Scenario: Canonical content wins
- **WHEN** a second source is linked to an existing canonical posting
- **THEN** the canonical posting's normalized fields are not overwritten by the second source

### Requirement: Posting language detection
The system SHALL detect the language of each posting from its title and description at ingest and store it as `en`, `de`, `fr`, `it` or `other`. A posting in `fr`, `it` or `other` SHALL be stored and flagged as out of scope for drafting, and SHALL NOT be dropped.

#### Scenario: German posting
- **WHEN** a posting written in German is ingested
- **THEN** its language is `de` and it is not flagged

#### Scenario: English posting
- **WHEN** a posting written in English is ingested
- **THEN** its language is `en` and it is not flagged

#### Scenario: French posting
- **WHEN** a posting written in French is ingested
- **THEN** its language is `fr`, it is stored, and it is flagged out of scope for drafting

#### Scenario: Too little text
- **WHEN** a posting's text is too short to detect a language reliably
- **THEN** its language is `other` and it is flagged for review rather than guessed

#### Scenario: Language re-detected on update
- **WHEN** a posting is updated with a description in a different language
- **THEN** the stored language reflects the new text

### Requirement: JobIngested event
The system SHALL publish a `JobIngested` event when a canonical posting is created. The publisher is `postings`. The payload is `postingId` and `ingestedAt` (UTC timestamp). It carries no posting text. There are no consumers yet; `matching` is expected to subscribe later. The event SHALL NOT be published for `updated` or `unchanged` results, or when a posting is linked to an existing canonical posting.

#### Scenario: Event on creation
- **WHEN** a posting creates a new canonical row
- **THEN** exactly one `JobIngested` event with that `postingId` is published and recorded by the event publication registry

#### Scenario: No event on repeat
- **WHEN** the same posting is ingested again
- **THEN** no `JobIngested` event is published

#### Scenario: No event on cross-source link
- **WHEN** a posting from a second source is linked to an existing canonical posting
- **THEN** no `JobIngested` event is published

### Requirement: Read access for other modules
The `postings` module SHALL expose, through its public API only, lookup of a canonical posting by id and a listing of canonical postings newest first with a page size limit. These reads SHALL return the same data to every caller, since postings are shared.

#### Scenario: Lookup by id
- **WHEN** another module asks for an existing posting id
- **THEN** it receives the normalized posting including language and the out-of-scope flag, but not the raw payload

#### Scenario: Unknown id
- **WHEN** another module asks for an id that does not exist
- **THEN** the result is empty, not an error

#### Scenario: Paged listing
- **WHEN** 30 postings exist and a page of 10 is requested
- **THEN** the 10 most recently first-seen postings are returned

#### Scenario: Shared across users
- **WHEN** two different users' requests read the same posting id
- **THEN** both receive identical data

### Requirement: No posting text in logs
The system SHALL NOT write posting descriptions or raw payloads to logs. Logs MAY contain posting ids, source ids and counts.

#### Scenario: Ingest logging
- **WHEN** a posting is ingested and a log line is written
- **THEN** the line contains no part of the description or raw payload
