# ui-cv-export Specification

## Purpose
Defines the export page of the web UI: choosing format, template, language and contents of a CV, previewing it and downloading it.

## Requirements

### Requirement: Export page
The UI SHALL provide a page at `/export`, reachable from the signed-in shell and from the profile page, with controls for format (Markdown, HTML, PDF), template (Classic, Modern, Compact), language (English, Deutsch), the sections to include (each a checkbox, in the default order, with move up/down), and the options "Open source projects", "Analysis summaries" (enabled only when open source projects are included) and "Work authorisation". A visitor who is not signed in SHALL see the landing page at that address.

#### Scenario: Defaults
- **WHEN** a signed-in user opens `/export`
- **THEN** format is PDF, template Classic, language English, every section ticked, open source projects on, analysis summaries off, work authorisation on

#### Scenario: Signed out
- **WHEN** a visitor without a session opens `/export`
- **THEN** the landing page is shown

#### Scenario: Sections chosen
- **WHEN** the user unticks Publications and moves Skills above Experience
- **THEN** the request sends `sections` in that order without `PUBLICATIONS`

### Requirement: Live preview
The page SHALL show a preview of the CV made with format `HTML` and the chosen template, language and contents, in an iframe with the `sandbox` attribute (no permissions) and the document passed as `srcdoc`, refreshed shortly after an option changes (at most one request in flight; the latest options win). While loading it SHALL show a loading state and on failure the fixed message of the error. The preview SHALL NOT be stored in the browser.

#### Scenario: Preview follows options
- **WHEN** the user switches the language to Deutsch
- **THEN** the preview reloads and shows German headings

#### Scenario: Sandboxed
- **WHEN** the preview is shown
- **THEN** the iframe has a `sandbox` attribute with no allowed capabilities

#### Scenario: Rapid changes
- **WHEN** the user changes three options within 200 ms
- **THEN** at most one request is sent for the final options (plus one that may already be in flight), and the preview shows the final result

### Requirement: Download
A "Download" button SHALL request the chosen format with the same options and save the response as a file named by the server's `Content-Disposition`, using the signed-in token and an object URL that is revoked afterwards. It SHALL show a loading state and the fixed message for each failure (`name_required` with a link to the profile's Contact section, `too_many_exports`, `busy`, `render_failed`, `invalid_request`, and the generic API messages). The page SHALL NOT keep the downloaded file or the profile in `localStorage`, `sessionStorage` or cookies and SHALL NOT log them.

#### Scenario: Download PDF
- **WHEN** the user presses Download with PDF selected
- **THEN** a `POST /api/cv/export` with format `PDF` is sent and the browser saves the returned file

#### Scenario: Name missing
- **WHEN** the server answers 409 `name_required`
- **THEN** the page tells the user to add their full name and links to the Contact section, in the preview area and for Download

#### Scenario: Limit
- **WHEN** the server answers 429 `too_many_exports`
- **THEN** a fixed message says to try again later and nothing is downloaded

#### Scenario: No browser storage
- **WHEN** a CV is previewed and downloaded
- **THEN** no profile text is present in any browser storage or console output
