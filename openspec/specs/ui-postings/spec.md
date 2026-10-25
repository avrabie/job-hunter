# ui-postings Specification

## Purpose
Defines the postings pages of the web UI: how a signed-in user browses the crawled postings, filters them and opens the original ads.

## Requirements

### Requirement: Postings list page
The UI SHALL provide a page at `/postings`, reachable from the signed-in shell, that loads `GET /api/postings` through the API client and shows, for each posting, its title, company, city, language, first-seen date and one "Open original" link per source. The list SHALL show a clear empty state and a loading state. A visitor who is not signed in SHALL see the landing page at that address.

#### Scenario: List
- **WHEN** a signed-in user opens `/postings`
- **THEN** the newest postings are listed, each with its company, city and original links

#### Scenario: Several sources
- **WHEN** a posting has links from two sources
- **THEN** two "Open original" links are shown, labelled by source

#### Scenario: Empty
- **WHEN** no posting matches
- **THEN** a message says so and no table of rows is shown

#### Scenario: Signed out
- **WHEN** a visitor without a session opens `/postings`
- **THEN** the landing page is shown and nothing is requested from the API

### Requirement: Search, filter and paging
The page SHALL offer a text search, a canton filter and a source filter, and next and previous page controls. Changing a filter SHALL return to the first page and reload. The search text SHALL be sent only to the API.

#### Scenario: Search
- **WHEN** the user types "kotlin" and submits
- **THEN** the request carries `q=kotlin` and the list shows the result

#### Scenario: Paging
- **WHEN** the user chooses next page
- **THEN** the request carries the next page number and the list changes; previous is disabled on the first page and next on the last

### Requirement: Safe original links
Original links SHALL be rendered only for `http` and `https` URLs, SHALL open in a new tab with `rel="noopener noreferrer"`, and a posting whose only link has another scheme SHALL show no link.

#### Scenario: Link attributes
- **WHEN** a posting with an https link is shown
- **THEN** the anchor opens in a new tab with `rel` containing `noopener` and `noreferrer`

#### Scenario: Unsafe scheme
- **WHEN** a link uses the `javascript:` scheme
- **THEN** no anchor is rendered for it

### Requirement: Posting detail page
The UI SHALL provide `/postings/:id` showing the posting's title, company, location, language, dates, the plain-text description (as text, never as HTML) and the original links. An unknown id SHALL show a not-found message with a link back to the list.

#### Scenario: Description is text
- **WHEN** a description contains `<script>` or `<b>`
- **THEN** the page shows those characters as text and no element is created from them

#### Scenario: Not found
- **WHEN** `/postings/<unknown id>` is opened
- **THEN** a not-found message and a link to `/postings` are shown

### Requirement: Failures are shown without response content
When a request fails, the page SHALL show a short fixed message and a retry control and SHALL NOT show or log response bodies.

#### Scenario: Server error
- **WHEN** the list request answers 500
- **THEN** a fixed error message with a retry button is shown and the page remains usable

### Requirement: Interested action
Each posting in the list and on the posting page SHALL have an "Interested" action for signed-in users. It tracks the posting and then shows the current stage with a link to the application. A posting already tracked SHALL show its stage from the start. A failure SHALL show a message and leave the posting as it was.

#### Scenario: Mark interested
- **WHEN** the user presses Interested
- **THEN** the posting shows the stage Interested and a link to the application
