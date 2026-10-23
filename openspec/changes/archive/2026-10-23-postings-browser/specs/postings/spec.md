# Spec Delta

## ADDED Requirements

### Requirement: Search and source links for other modules
The `postings` module SHALL expose, through its public API, a search over canonical postings (optional text over title and company, optional canton, optional source id, page and size of at most 100, newest first-seen first). Every returned posting SHALL carry its original links, one per source that delivered it, the primary source first, and never the raw payload.

#### Scenario: Text search
- **WHEN** postings titled "Kotlin Developer" and "Graphic Designer" exist and the text "kotlin" is searched
- **THEN** only the first is returned

#### Scenario: Company match
- **WHEN** the text "acme" is searched and a posting's company is "ACME AG"
- **THEN** that posting is returned

#### Scenario: Filters combine
- **WHEN** canton "ZH" and source "greenhouse-proton" are both given
- **THEN** only postings in ZH that have a link from that source are returned

#### Scenario: Two sources, two links
- **WHEN** one canonical posting was delivered by two sources
- **THEN** it is returned once, with two links, the primary source's first

#### Scenario: Wildcard characters are literal
- **WHEN** the text "100%" or "a_b" is searched
- **THEN** `%` and `_` match only themselves, not any text

### Requirement: Postings endpoints
The system SHALL serve `GET /api/postings` and `GET /api/postings/{id}` to any caller with a valid access token. The list SHALL take `page` (from 0, default 0), `size` (default 20, at most 100), `q`, `canton` and `source`, and answer postings with id, title, company, city, canton, language, `firstSeen`, `lastSeen` and `links`. The detail SHALL add the plain-text description and answer 404 for an unknown id. A negative page SHALL be answered 400. Neither endpoint SHALL return the raw payload.

#### Scenario: Signed-in list
- **WHEN** a signed-in user requests `GET /api/postings`
- **THEN** the answer is 200 with up to 20 postings, newest first-seen first, each with its links

#### Scenario: Not signed in
- **WHEN** no token is sent
- **THEN** the answer is 401

#### Scenario: Detail
- **WHEN** a signed-in user requests an existing posting
- **THEN** the answer holds the description text and all links, and no raw payload

#### Scenario: Unknown posting
- **WHEN** an id that does not exist is requested
- **THEN** the answer is 404

#### Scenario: Paging and cap
- **WHEN** `size=1000` is requested
- **THEN** at most 100 postings are returned; `page=-1` is answered 400

#### Scenario: Search text is not logged
- **WHEN** a request carries `q=SEARCHMARKER`
- **THEN** the marker never appears in the logs
