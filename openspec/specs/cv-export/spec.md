# cv-export Specification

## Purpose
Defines how the caller's saved profile is turned into a CV document (Markdown, HTML or PDF) in a chosen template and language, what is printed, and the safety and limit rules of that rendering.

## Requirements

### Requirement: Export the saved profile
`POST /api/cv/export` with `{format, template, language, sections?, includeOpenSource?, includeAnalysis?, includeWorkAuthorisation?}` SHALL, for a caller with a valid token, render the caller's saved profile and answer with the file bytes. `format` SHALL be `MARKDOWN`, `HTML` or `PDF`, `template` `CLASSIC`, `MODERN` or `COMPACT`, `language` `EN` or `DE`. The body SHALL NOT carry profile content and no user id SHALL be accepted: the profile is read by the token's `sub`. The response SHALL have the content type of the format (`text/markdown; charset=utf-8`, `text/html; charset=utf-8`, `application/pdf`), `Content-Disposition: attachment` with an ASCII file name `<name-slug>-cv-<language>.<md|html|pdf>`, and `Cache-Control: no-store`. The call SHALL NOT change the stored profile. An unknown value, an unknown field or an invalid body SHALL answer 400 `invalid_request`.

#### Scenario: Export as PDF
- **WHEN** a user with a saved profile with a full name requests format `PDF`, template `MODERN`, language `DE`
- **THEN** the response is 200 `application/pdf`, the bytes start with `%PDF`, and the extracted text contains the user's name

#### Scenario: Export as Markdown and HTML
- **WHEN** the same user requests `MARKDOWN` and then `HTML`
- **THEN** each answer is 200 with its content type and holds the user's name and the same sections in the same order

#### Scenario: Profile unchanged
- **WHEN** a user exports
- **THEN** their stored profile and its version are unchanged

#### Scenario: Another user's profile is unreachable
- **WHEN** users A and B each saved a profile and A exports
- **THEN** the CV holds only A's data

#### Scenario: Anonymous call
- **WHEN** the endpoint is called without a valid token
- **THEN** the response is 401

#### Scenario: Bad request
- **WHEN** the body has `format: "DOCX"`, or an unknown field, or is not JSON
- **THEN** the response is 400 `invalid_request` and nothing is rendered

#### Scenario: No profile content in the request
- **WHEN** a request carries a `profile` field
- **THEN** the response is 400 `invalid_request`

### Requirement: Name is required to export
Export SHALL refuse a profile whose `contact.fullName` is null or blank, or a user who never saved a profile, with 409 `name_required`.

#### Scenario: No name
- **WHEN** the saved profile has no full name
- **THEN** the response is 409 `name_required` and nothing is rendered

#### Scenario: Never saved
- **WHEN** a user who never saved a profile exports
- **THEN** the response is 409 `name_required`

### Requirement: What the CV contains
The CV SHALL print, in this order and each only when it has content: a header (full name, headline, contact lines for email, phone, location, website, LinkedIn and the linked code accounts as links, and when `includeWorkAuthorisation` is true the work authorisation in words), then the sections Profile (summary), Experience, Open source projects, Education, Skills, Languages and Publications. `sections` MAY select and order the sections; an empty list SHALL print only the header. Experience and education SHALL show their period (month and year; no end month prints "Present" or "heute"), place, achievements as bullets and technologies as tags. Open source projects SHALL appear only when `includeOpenSource` is true and show name with its link, language, stars, description, the user's note and, when `includeAnalysis` is true and the project has an analysis, its summary and the technologies of its tech stack. Skills SHALL show the level in words when set. Languages SHALL show the language name and level in words. The CV SHALL NOT print preferences, target roles, workload, salary, cantons, skill sources or any field not listed here, and SHALL NOT contain text that is not in the profile or a fixed label.

#### Scenario: Order and omission
- **WHEN** a profile has experience and skills but no education, publications or open source projects
- **THEN** the CV has a header, Experience and Skills in that order, and no empty headings

#### Scenario: Selected sections
- **WHEN** `sections` is `["SKILLS","EXPERIENCE"]`
- **THEN** the CV has the header, then Skills, then Experience, and nothing else

#### Scenario: Current position
- **WHEN** an experience entry has no end month
- **THEN** its period reads `Mar 2021 – Present` in English and `03.2021 – heute` in German

#### Scenario: Open source with and without analysis
- **WHEN** a project has an analysis and `includeAnalysis` is true, then false
- **THEN** its summary and technologies appear in the first CV and neither in the second, while its name, link and description appear in both

#### Scenario: Open source off
- **WHEN** `includeOpenSource` is false
- **THEN** no open source heading or project appears

#### Scenario: Private data never printed
- **WHEN** the profile has a minimum salary of 150000, target roles and cantons
- **THEN** none of them appears in the Markdown, HTML or the text of the PDF

#### Scenario: Work authorisation
- **WHEN** the work authorisation is `PERMIT_B` and `includeWorkAuthorisation` is true, then false
- **THEN** the header shows it in words in the first CV and not in the second

### Requirement: Languages and wording
Section headings, labels, months, the words for "present", skill levels, language levels and work authorisations SHALL follow `language`: English, or German in Swiss spelling (no `ß`). Language names SHALL be shown in the chosen language. The user's own text (summary, achievements, descriptions, notes, titles) SHALL be printed as written and never translated.

#### Scenario: German labels
- **WHEN** `language` is `DE`
- **THEN** the headings read Profil, Berufserfahrung, Open-Source-Projekte, Ausbildung, Kenntnisse, Sprachen and Publikationen, and no label contains `ß`

#### Scenario: English labels
- **WHEN** `language` is `EN`
- **THEN** the headings read Profile, Experience, Open source projects, Education, Skills, Languages and Publications

#### Scenario: User text untouched
- **WHEN** an achievement is written in French
- **THEN** it appears unchanged in both language versions

### Requirement: Templates
`CLASSIC` SHALL be a single-column layout without colour. `MODERN` SHALL be a two-column layout with contact, skills and languages in the narrow column and an accent colour. `COMPACT` SHALL be a single-column layout with smaller type and tighter spacing. The three templates SHALL print the same content; only the layout differs. The PDF SHALL be A4 with selectable text, SHALL paginate long content without cutting entries mid-line, and SHALL embed its fonts. Markdown SHALL use the same structure for every template.

#### Scenario: Same content
- **WHEN** the same profile is exported in the three templates
- **THEN** the extracted text of each holds the same name, the same section headings and the same entries

#### Scenario: Selectable text and fonts
- **WHEN** a PDF is exported for a profile with German and French text (`ä ö ü é è ç`)
- **THEN** the text extracted from the PDF contains those characters, and every font in the PDF is embedded

#### Scenario: Compact fits
- **WHEN** a profile with two positions and ten skills is exported as `COMPACT` PDF
- **THEN** the PDF has one page

#### Scenario: Long profiles paginate
- **WHEN** a profile with 30 experience entries is exported as PDF
- **THEN** the PDF has more than one page and every entry is present in the extracted text

### Requirement: Safe output
Every profile string SHALL be escaped before it enters HTML, so that markup in the profile (`<script>`, `<img onerror=…>`, quotes, `&`) is shown as text and never interpreted. Links SHALL be emitted only for `http` and `https` addresses. The standalone HTML SHALL forbid scripts and external resources. The PDF renderer SHALL make no network request and read no file other than its bundled fonts and styles. Markdown SHALL escape the characters that would turn profile text into structure or links.

#### Scenario: Markup is text
- **WHEN** an achievement is `<script>alert(1)</script> & "quoted"`
- **THEN** the HTML holds `&lt;script&gt;` and `&amp;` and no `<script` tag, and the PDF text shows the characters as written

#### Scenario: Unsafe link dropped
- **WHEN** a link in the data is not `http` or `https`
- **THEN** it is printed as plain text without a link

#### Scenario: No outside requests
- **WHEN** a PDF is rendered for a profile whose text mentions `http://127.0.0.1:1/x.png` and `<img src="http://127.0.0.1:1/x.png">`
- **THEN** no connection is opened and the render still succeeds

#### Scenario: Markdown structure not injectable
- **WHEN** a summary line starts with `# Heading` or contains `[x](javascript:alert(1))`
- **THEN** the Markdown shows them as text, not as a heading or a link

### Requirement: Limits and failures
The endpoint SHALL allow at most 30 exports per user in any rolling hour (configurable) with its own counter, answering 429 `too_many_exports` beyond that, before any rendering. At most 2 PDF renders SHALL run at the same time; a request that cannot start within 5 seconds SHALL answer 503 `busy`. A rendering error SHALL answer 500 `render_failed` with a fixed body. No profile text SHALL appear in logs, error bodies or exception messages.

#### Scenario: Hourly limit
- **WHEN** a user has made 30 exports in the last hour and asks for another
- **THEN** the response is 429 `too_many_exports` and nothing is rendered

#### Scenario: Allowance is per user
- **WHEN** user A has used the allowance
- **THEN** user B can still export

#### Scenario: Busy
- **WHEN** two PDF renders are running and a third cannot start within 5 seconds
- **THEN** the third answers 503 `busy`

#### Scenario: Nothing in logs
- **WHEN** an export succeeds or fails
- **THEN** the captured application log contains none of the profile's text

### Requirement: Contact visibility
The CV builder SHALL accept which contact items to print: email, phone, website, LinkedIn, linked code accounts, location and headline, each on or off. When the option is not given all of them SHALL be on, so the export endpoint behaves as before. An item that is off SHALL NOT appear anywhere in the document, in any format or template. The full name SHALL always be printed.

#### Scenario: Default prints everything
- **WHEN** a profile with every contact item is exported without the option
- **THEN** every item appears, as before this requirement

#### Scenario: Items off
- **WHEN** email and phone are off and the rest on
- **THEN** neither the email address nor the phone number appears in the Markdown, the HTML or the text of the PDF, and the other items do

#### Scenario: All off
- **WHEN** every item is off
- **THEN** the header shows only the full name, in every template and language
