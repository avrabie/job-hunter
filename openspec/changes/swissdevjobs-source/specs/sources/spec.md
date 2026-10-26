## ADDED Requirements

### Requirement: SwissDevJobs source
The system SHALL ingest the jobs of SwissDevJobs from its public JSON job list and its public RSS feed, using the same politeness, `robots.txt`, minimum-interval and failure rules as every adapter. It SHALL read `robots.txt` of the host and SHALL NOT request the list or the feed if the rules for `User-agent: *` disallow that path. The adapter SHALL be switchable with a setting and SHALL be on by default. Every job on the board is taken to be a Swiss IT job; no location or role filter applies.

#### Scenario: Run
- **WHEN** a crawl runs with the adapter enabled and the board lists 3 jobs, each with a feed item
- **THEN** 3 postings are created with source `swissdevjobs`, and the result for `swissdevjobs` is completed with created = 3

#### Scenario: Disabled
- **WHEN** the adapter is switched off by its setting
- **THEN** a crawl makes no request to the board and the result has no `swissdevjobs` entry

#### Scenario: Blocked by robots.txt
- **WHEN** `robots.txt` disallows the list path for all crawlers
- **THEN** the list is not requested and the result for `swissdevjobs` is blocked by robots.txt

#### Scenario: Board unreachable
- **WHEN** the list request fails or times out
- **THEN** the result for `swissdevjobs` is failed with a short fixed reason, nothing is ingested, and the stored validator is unchanged

### Requirement: SwissDevJobs job mapping
A job SHALL be stored with its title, company, the SwissDevJobs page of the job as URL (without tracking parameters), city, remote mode, yearly salary range and description, where the structured fields come from the list and the description comes from the feed item whose link has the same slug. The remote mode SHALL be remote, hybrid or on-site according to the board's workplace value, and unknown when the value is missing or unrecognised. The salary range SHALL be set only when both amounts are present and positive, and is taken to be yearly CHF. The description SHALL be the feed item's HTML converted to plain text. The employer's apply link SHALL NOT be stored.

#### Scenario: Full job
- **WHEN** a list entry has a company, city Zug, workplace hybrid, salary 80000 to 120000, and the feed has an item with the same slug and a description
- **THEN** the posting has that company, city Zug, hybrid, salary 80000 to 120000, a URL of the form `https://swissdevjobs.ch/jobs/<slug>`, and the description as plain text

#### Scenario: No salary
- **WHEN** a list entry has no salary amounts
- **THEN** the posting is stored with salary unknown

#### Scenario: Only one salary amount
- **WHEN** a list entry has a lower amount and no upper amount
- **THEN** the posting is stored with salary unknown

#### Scenario: Unknown workplace
- **WHEN** a list entry's workplace is missing or a value that is not remote, hybrid or on-site
- **THEN** the remote mode is unknown

#### Scenario: Same job on two sources
- **WHEN** a job is already stored from a Greenhouse board with the same title, company and city
- **THEN** the board's job is recorded as a second source of that posting and no second posting is created

### Requirement: Jobs without a description are skipped
A list entry for which the feed has no item with the same slug, or whose item has an empty description, SHALL NOT be stored. It SHALL be counted as skipped and SHALL be tried again on the next run: the run SHALL NOT keep a list validator that would make the next run end as not modified while such a job is outstanding. A paused job SHALL be counted as filtered out and not stored.

#### Scenario: Partner ad without text
- **WHEN** a list entry has no matching feed item
- **THEN** it is counted as skipped, the other jobs are still ingested, and the next run requests the list again

#### Scenario: Paused job
- **WHEN** a list entry is marked paused
- **THEN** it is counted as filtered out and nothing is stored for it

### Requirement: Untrusted feed and list content
The list and the feed SHALL be treated as untrusted. The feed SHALL be parsed without resolving external entities or document type definitions, and a body larger than the configured limit SHALL end the run as failed. A list or feed that cannot be parsed SHALL end the run as failed with the reason `malformed list` or `malformed feed`, and nothing from that run is ingested.

#### Scenario: Entity in the feed
- **WHEN** the feed contains a document type definition with an external entity
- **THEN** the entity is not resolved, and the run ends as failed with `malformed feed` or ingests without the entity's content

#### Scenario: Malformed list
- **WHEN** the list body is not a JSON array of jobs
- **THEN** the run for `swissdevjobs` is failed with `malformed list` and nothing is ingested

#### Scenario: One bad entry
- **WHEN** one list entry has no title
- **THEN** that entry is counted as skipped and the others are ingested

### Requirement: Raw payload of a SwissDevJobs job
The raw payload stored for a job SHALL be the list entry and the feed item's description as the board delivered them, unchanged.

#### Scenario: Payload
- **WHEN** a job is ingested
- **THEN** its stored raw payload contains the list entry's fields and the feed item's description text

### Requirement: One crawl covers every source
A crawl run SHALL cover the Greenhouse boards and then SwissDevJobs, and return the results of all of them in one list. The scheduled run, the startup run and the schedule API SHALL behave as before. A source that fails or throws SHALL NOT prevent the other from running.

#### Scenario: Both sources
- **WHEN** a crawl runs with Greenhouse boards configured and SwissDevJobs enabled
- **THEN** the result lists every Greenhouse board result followed by the `swissdevjobs` result

#### Scenario: Greenhouse fails
- **WHEN** every Greenhouse board fails
- **THEN** SwissDevJobs is still crawled

#### Scenario: SwissDevJobs throws
- **WHEN** the SwissDevJobs adapter throws an unexpected error
- **THEN** its result is failed with a short fixed reason and the Greenhouse results are unaffected

### Requirement: No SwissDevJobs text in logs
The adapter SHALL log only the outcome, counts and HTTP statuses of a run. Job titles, companies, descriptions, URLs of jobs and response bodies SHALL NOT be logged.

#### Scenario: Run logging
- **WHEN** a run ingests jobs whose titles and descriptions contain marker words
- **THEN** no log line contains a marker word
