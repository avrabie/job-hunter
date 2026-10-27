## ADDED Requirements

### Requirement: Public postings endpoint
The system SHALL serve `GET /api/public/postings` to any caller without a token. The answer SHALL list postings newest first-seen first, ties broken by id, and SHALL hold `total` (the number of postings in the filtered window), `page`, `size` and, for each posting, only: id, title, company, city, canton, remote mode, salary range, language, first-seen date and its source links (label and original URL). It SHALL NOT contain the description, the raw payload or the last-seen time. The endpoint SHALL take `page` (from 0), `size` (default 20, at most 50), `days` (1 to 31), `since` and `until` (ISO dates, inclusive, Zurich time), `canton` and `language` (en or de). With no date given the window SHALL be the last 14 days. A window longer than 31 days, a `since` more than 90 days ago, a negative page, an invalid value, or `page` times `size` above 1000 SHALL be answered 400. Query text SHALL NOT be a parameter. The answer SHALL be cacheable by anyone for 60 seconds.

#### Scenario: Default list
- **WHEN** a visitor without a token requests `GET /api/public/postings`
- **THEN** the answer is 200 with postings first seen in the last 14 days, newest first, at most 20, each with its links and no description

#### Scenario: Last two weeks
- **WHEN** `days=14` is requested and postings were first seen 3 days ago and 20 days ago
- **THEN** only the posting from 3 days ago is listed

#### Scenario: Date range
- **WHEN** `since` and `until` name a 7-day range
- **THEN** only postings first seen inside it (both days included) are listed

#### Scenario: Paging is stable
- **WHEN** three postings share one first-seen time and pages of size 2 are requested
- **THEN** every posting appears exactly once across the pages

#### Scenario: Size cap
- **WHEN** `size=500` is requested
- **THEN** at most 50 postings are returned

#### Scenario: Bad window
- **WHEN** `since` is 40 days before `until`, or `since` is 120 days ago, or `page=-1`, or `days=0`, or `page=100&size=20`
- **THEN** the answer is 400

#### Scenario: No text fields
- **WHEN** a posting has a description and a raw payload
- **THEN** neither appears in the answer

#### Scenario: Filter by canton and language
- **WHEN** `canton=ZH&language=de` is requested
- **THEN** only German-language postings in canton ZH are listed

#### Scenario: Cache header
- **WHEN** the endpoint answers 200
- **THEN** the response allows public caching for 60 seconds

#### Scenario: Out-of-scope languages
- **WHEN** `language=fr` is requested
- **THEN** the answer is 400

### Requirement: Public postings are rate limited
The public endpoint SHALL answer 429 with a `Retry-After` header when one client address exceeds the configured number of requests per minute (default 60). The limit SHALL be counted per client address as set by the edge, SHALL NOT affect other clients, and SHALL keep a bounded amount of state. Client addresses SHALL NOT be written to logs.

#### Scenario: Over the limit
- **WHEN** one address makes 61 requests within a minute
- **THEN** the 61st is answered 429 with `Retry-After` and another address is still served

#### Scenario: Bounded state
- **WHEN** requests arrive from a very large number of distinct addresses
- **THEN** the limiter's memory stays bounded and old entries are dropped

#### Scenario: No address in logs
- **WHEN** a request is limited
- **THEN** no log line contains the client address
