# sources Specification

## Purpose

Defines how source adapters fetch job postings from outside systems and hand them to the shared posting store: the politeness rules every adapter follows, how failed or partial fetches are handled, and what the Greenhouse adapter keeps and how it maps a job to a posting.

## Requirements

### Requirement: Identifying and polite requests
Every request a source adapter makes SHALL carry a `User-Agent` that names the application and a way to reach its operator, SHALL use connect and read timeouts, and SHALL NOT run in parallel with another request to the same host. Consecutive requests to one host SHALL be separated by a pause (default 1 second).

#### Scenario: User-Agent
- **WHEN** an adapter makes a request
- **THEN** the request carries the configured `User-Agent`, and the default names the application and `https://s4v3.net`

#### Scenario: Pause between requests
- **WHEN** an adapter makes two requests to the same host in one run
- **THEN** the second starts at least the configured pause after the first finished

#### Scenario: Slow server
- **WHEN** the server does not answer within the read timeout
- **THEN** that board's run ends as failed, nothing from it is ingested, and the failure is reported without response content

### Requirement: robots.txt is respected
Before fetching from a host in a run, an adapter SHALL read the host's `robots.txt` and SHALL NOT fetch a URL that the rules for `User-agent: *` disallow. If `robots.txt` cannot be read because of a server error or timeout, the run for that host SHALL end as failed without fetching content. A missing `robots.txt` (404) allows fetching.

#### Scenario: Path allowed
- **WHEN** `robots.txt` disallows only `/embed/` and the adapter reads `/v1/boards/acme/jobs`
- **THEN** the request is made

#### Scenario: Path disallowed
- **WHEN** `robots.txt` disallows the board path for all crawlers
- **THEN** the board is not requested and its result is reported as blocked by robots.txt

#### Scenario: Wildcard and end-anchor rules
- **WHEN** `robots.txt` contains `Disallow: /*.txt$`
- **THEN** `/a/b.txt` is disallowed and `/a/b.txt.html` and `/v1/boards/acme/jobs` are allowed

#### Scenario: robots.txt unreadable
- **WHEN** the `robots.txt` request fails with a server error or times out
- **THEN** no content is fetched from that host and the run for it ends as failed

### Requirement: Conditional fetch and minimum interval
The system SHALL remember the `ETag` of each board's last successful list fetch and send it as `If-None-Match`. A `304 Not Modified` answer SHALL end that board's run as not modified without fetching jobs or ingesting. A board's run SHALL NOT start before its minimum interval (default 15 minutes) has passed since its last run started; such a board is reported as too soon and no request is made for it.

#### Scenario: Not modified
- **WHEN** the server answers 304 to the list request
- **THEN** no job detail is requested, nothing is ingested, and the board is reported as not modified

#### Scenario: Validator remembered across restarts
- **WHEN** the application restarts after a successful fetch and crawls the board again
- **THEN** the list request carries the `ETag` stored by the earlier run

#### Scenario: Validator updated only on success
- **WHEN** a list fetch fails or its body is rejected
- **THEN** the stored `ETag` is not changed

#### Scenario: Board too soon
- **WHEN** a board's run starts less than the minimum interval after its previous run started
- **THEN** no request is made for it and it is reported as too soon

### Requirement: Bounded and safe response handling
An adapter SHALL accept only a successful response with a JSON content type, SHALL stop reading a body that exceeds its size limit (default 8 MB for a list and 1 MB for one job) and treat it as failed, and SHALL treat malformed JSON as failed.

#### Scenario: Oversized body
- **WHEN** a response body exceeds its size limit
- **THEN** that fetch ends as failed and nothing from it is ingested

#### Scenario: Error status
- **WHEN** the server answers with a 4xx or 5xx status
- **THEN** that fetch ends as failed and the stored `ETag` is unchanged

#### Scenario: Not JSON
- **WHEN** the server answers 200 with an HTML page or malformed JSON
- **THEN** that fetch ends as failed and nothing from it is ingested

### Requirement: Configured Greenhouse boards
The Greenhouse adapter SHALL crawl exactly the boards named in its configured list, one after the other. A board that does not exist (404) or fails SHALL be reported as failed and SHALL NOT stop the remaining boards. With an empty list a run SHALL make no request.

#### Scenario: Boards crawled in turn
- **WHEN** the list holds boards `a` and `b`
- **THEN** both are crawled, one after the other, and the result has one entry per board

#### Scenario: Unknown board
- **WHEN** a configured board answers 404
- **THEN** it is reported as failed and the next board is still crawled

#### Scenario: Empty list
- **WHEN** no boards are configured
- **THEN** a run makes no request and returns an empty result

### Requirement: Swiss jobs only
The adapter SHALL keep a job only when its location text names Switzerland or a Swiss city (for example Zurich, Zürich, Geneva, Genève, Basel, Bern, Lausanne, Zug, Lucerne, Luzern, Winterthur, Lugano, St. Gallen) as a whole word, ignoring case. A job with several places is kept if any is Swiss. Other jobs SHALL be counted as filtered out, their details SHALL NOT be requested, and nothing of them is stored.

#### Scenario: City only
- **WHEN** a job's location is `Zurich; `
- **THEN** the job is kept

#### Scenario: Several places
- **WHEN** a job's location is `Geneva; Taipei; Skopje`
- **THEN** the job is kept

#### Scenario: Country only
- **WHEN** a job's location is `Remote - Switzerland`
- **THEN** the job is kept

#### Scenario: Not Swiss
- **WHEN** a job's location is `Warsaw`, `Berne, Indiana` or `Bernardo`
- **THEN** the job is filtered out and its detail is not requested

### Requirement: IT roles only
The adapter SHALL keep a job only when its title contains an IT keyword as a whole word, ignoring case, and none of the excluded keywords. Both keyword lists SHALL be configurable. Jobs that are not kept SHALL be counted as filtered out.

#### Scenario: Default lists
- **WHEN** the adapter runs with default settings
- **THEN** the IT keywords include engineer, engineering, developer, software, devops, sre, reliability, architect, scientist, machine learning, security and administrator, and the excluded keywords include sales, account, recruiter, marketing, legal, finance, counsel and product manager

#### Scenario: Engineering title
- **WHEN** a Swiss job is titled `Senior Site Reliability Engineer, Infrastructure`
- **THEN** it is kept

#### Scenario: Student role
- **WHEN** a Swiss job is titled `Working Student, Security Engineer`
- **THEN** it is kept

#### Scenario: Business title
- **WHEN** a Swiss job is titled `Head of Growth` or `Group Product Manager, B2C`
- **THEN** it is filtered out

#### Scenario: Engineering management
- **WHEN** a Swiss job is titled `Engineering Manager, Partner Tech`
- **THEN** it is kept

#### Scenario: Excluded keyword wins
- **WHEN** a Swiss job is titled `Sales Engineer`
- **THEN** it is filtered out

#### Scenario: Custom keywords
- **WHEN** the keyword list is configured as `analyst` only
- **THEN** a Swiss job titled `Data Analyst` is kept and `Software Engineer` is filtered out

### Requirement: Greenhouse job mapping
The adapter SHALL hand each kept job to the posting store with source id `greenhouse-<board>` (the board token), the job's id as the source posting id, its public URL as the URL, its title, and its company name as the company. City and canton SHALL be those of the first Swiss place in the location text when it is a known city, and unknown otherwise. Workload, remote mode and salary SHALL be left unknown.

#### Scenario: Full job
- **WHEN** board `acme` has a job with id `123`, title `Backend Engineer`, company name `Acme`, location `Zurich` and URL `https://acme.example/jobs?gh_jid=123`
- **THEN** the posting has source id `greenhouse-acme`, source posting id `123`, that title, company and URL, city `Zurich` and canton `ZH`

#### Scenario: Several places
- **WHEN** the location is `Paris; Geneva; Barcelona`
- **THEN** the city is `Geneva` and the canton is `GE`

#### Scenario: Country only
- **WHEN** the location is `Switzerland`
- **THEN** the city and canton are unknown

#### Scenario: Salary, workload and remote mode unknown
- **WHEN** any job is ingested
- **THEN** its salary, workload and remote mode are unknown

#### Scenario: Same job on two boards of different sources
- **WHEN** a job with the same company, title and city is also delivered by another source
- **THEN** the posting store links them to one canonical posting, as it does for any source

### Requirement: Greenhouse description text
The adapter SHALL build the posting description as plain text from the job's HTML content: the escaped HTML in the API's `content` field is decoded first, then tags are removed, list entries are put on separate lines, headings and paragraphs are separated by blank lines, and entities are decoded.

#### Scenario: Escaped HTML
- **WHEN** the content is `&lt;p&gt;Build &amp;amp; run services&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Kotlin&lt;/li&gt;&lt;li&gt;Postgres&lt;/li&gt;&lt;/ul&gt;`
- **THEN** the description contains `Build & run services` and the lines `- Kotlin` and `- Postgres`, and no angle-bracket tags

#### Scenario: Empty content
- **WHEN** a job has no content
- **THEN** it is counted as skipped and not ingested

### Requirement: Raw payload per job
The adapter SHALL store the job's detail JSON as the posting's raw payload, so a posting can be re-parsed without a new fetch.

#### Scenario: Payload is the job
- **WHEN** a job is ingested
- **THEN** the stored raw payload is that job's JSON object and contains no other job

### Requirement: Failures are isolated
A malformed job SHALL be skipped and counted without stopping the board or the run. A job is malformed when its id, title or URL is missing or empty, when its detail cannot be fetched or parsed, or when the posting store rejects it. A board that fails SHALL NOT affect the other boards.

#### Scenario: One bad job
- **WHEN** a board lists 3 Swiss IT jobs and the detail request of the second answers 500
- **THEN** the first and third are ingested, the second is counted as skipped, and the board is reported as completed

#### Scenario: One bad board
- **WHEN** the first of two boards fails
- **THEN** the second is still crawled and ingested

### Requirement: Run result and idempotency
A run SHALL return one result per board with its outcome (`completed`, `not modified`, `too soon`, `blocked by robots.txt`, `failed`) and, for a completed board, the counts of created, updated, unchanged, skipped and filtered-out jobs. Repeating a run on identical content SHALL create no posting and report every kept job as unchanged.

#### Scenario: First run
- **WHEN** a board lists 5 jobs of which 3 are Swiss IT roles
- **THEN** the result is `completed` with 3 created, 2 filtered out and none skipped

#### Scenario: Second run, same content
- **WHEN** the board is crawled again after the minimum interval and the list answers 200
- **THEN** 0 are created, 3 are unchanged, and the posting count is unchanged

#### Scenario: Changed job
- **WHEN** one job's content changed between runs
- **THEN** that job is counted as updated and the others as unchanged

### Requirement: Runs do not overlap
Only one crawl of a given board SHALL run at a time, also across application replicas. A crawl requested for a board that is already being crawled SHALL skip that board and report it as already running.

#### Scenario: Parallel start
- **WHEN** two crawls are started at the same moment
- **THEN** each board is crawled by one of them, the other reports it as already running, and each board's list is requested once

### Requirement: Opt-in run at startup
The application SHALL start a Greenhouse crawl at startup only when the setting for it is on. The setting SHALL be off by default.

#### Scenario: Default
- **WHEN** the application starts with default settings
- **THEN** no request is made to any board

#### Scenario: Enabled
- **WHEN** the application starts with the setting on
- **THEN** one crawl is started after startup and its per-board outcomes are logged

### Requirement: No posting text in logs
Source adapters SHALL NOT write job titles, descriptions, payloads or response bodies to logs. Logs MAY contain the source id, board tokens, outcomes, counts, durations and HTTP status.

#### Scenario: Run logging
- **WHEN** a crawl completes, fails or skips jobs
- **THEN** the log lines contain no part of any job's title, description or payload
