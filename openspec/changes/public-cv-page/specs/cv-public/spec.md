# cv-public Specification

## Purpose

Defines the public CV page: the handle a user chooses, what the owner makes visible, how the page is served without sign-in and kept safe, the owner's management endpoints, the preview, the administrator's takedown, and what happens when an account is deleted.

## ADDED Requirements

### Requirement: Handle
A user MAY claim one handle: 3 to 30 characters of lowercase letters, digits and single hyphens, not starting or ending with a hyphen, not all digits, and not on the reserved list (`admin`, `api`, `auth`, `login`, `register`, `cv`, `export`, `profile`, `files`, `matches`, `postings`, `publish`, `public`, `settings`, `www`, `static`, `assets`, `health`, `docs`, `new`, `edit` and the others of the design). Input SHALL be lower-cased before checking. A handle SHALL be unique across users, compared case-insensitively. A claimed handle that the owner stops using SHALL be held for 90 days: no other user can claim it, and the owner can take it back. A handle SHALL be changeable at most once every 30 days. Violations SHALL answer with a field message that never repeats the submitted value.

#### Scenario: Valid handle
- **WHEN** a user saves the handle `Ada-Example`
- **THEN** it is stored as `ada-example`

#### Scenario: Invalid forms
- **WHEN** a handle is `ab`, `-ada`, `ada--x`, `ada_x`, 31 characters long or `123456`
- **THEN** the response is 400 and `fields` contains `handle`

#### Scenario: Reserved
- **WHEN** a user saves the handle `admin` or `Profile`
- **THEN** the response is 409 `handle_reserved`

#### Scenario: Taken
- **WHEN** user B saves the handle that user A has
- **THEN** the response is 409 `handle_taken`, and A's page is unchanged

#### Scenario: Held after a change
- **WHEN** user A changes from `ada-example` to `ada-new`, and user B then tries `ada-example` within 90 days
- **THEN** the response is 409 `handle_held`, and A can take `ada-example` back

#### Scenario: Free after the hold
- **WHEN** 90 days have passed since A released `ada-example`
- **THEN** user B can claim it

#### Scenario: Change too soon
- **WHEN** user A changed the handle 10 days ago and changes it again
- **THEN** the response is 409 `handle_change_too_soon` and `handleChangeableAt` of `GET /api/cv/public` shows the date

#### Scenario: Availability check
- **WHEN** a signed-in user asks `GET /api/cv/public/handle?handle=ada-example`
- **THEN** the answer says available, or unavailable with the reason `invalid`, `reserved`, `taken` or `held`, and the user's own current handle is available

### Requirement: Visibility settings
The owner SHALL choose what is public with a settings object: `template` (`CLASSIC`, `MODERN`, `COMPACT`), `language` (`EN`, `DE`), the `sections` to print (an ordered list of `PROFILE`, `EXPERIENCE`, `OPEN_SOURCE`, `EDUCATION`, `SKILLS`, `LANGUAGES`, `PUBLICATIONS`), `headline`, `analysis`, `workAuthorisation`, `location`, `contact` (`email`, `phone`, `website`, `linkedin`, `codeAccounts`) and `indexable`. Every flag SHALL default to off and `sections` to empty, so that nothing but the full name is public until the owner ticks it. The page SHALL print only what the settings select, built by the same builder as the export. Preferences (target roles, workload, remote modes, cantons, minimum salary) and anything not listed SHALL NEVER be printed. Unknown settings fields SHALL be rejected with 400.

#### Scenario: Defaults are private
- **WHEN** a user publishes with the default settings
- **THEN** the page shows only the full name (and no email, phone, headline, location, work authorisation, section or link)

#### Scenario: Ticked items appear
- **WHEN** the owner ticks `EXPERIENCE`, `SKILLS`, `headline` and `contact.website`
- **THEN** the page shows the name, the headline, the website, and the experience and skills sections, and nothing else

#### Scenario: Preferences never
- **WHEN** the profile has a minimum salary of 150000, target roles and cantons and every option is ticked
- **THEN** none of them appears on the page

#### Scenario: Order of sections
- **WHEN** `sections` is `["SKILLS","EXPERIENCE"]`
- **THEN** Skills is printed above Experience

#### Scenario: Unknown field
- **WHEN** the settings contain an unknown field or `template: "FANCY"`
- **THEN** the response is 400 and nothing is stored

### Requirement: Publish, pause and remove
`PUT /api/cv/public` SHALL save the handle, the settings and the `published` flag for the caller's own page; `GET /api/cv/public` SHALL return the current state; `DELETE /api/cv/public` SHALL remove the settings and put the handle on hold. Saving with `published: false` SHALL pause the page: it answers 404 and the settings stay. Publishing SHALL require a full name in the profile (409 `name_required`). A page locked by an administrator SHALL refuse publishing (409 `locked`). No endpoint SHALL accept a user id; everything is by the token's `sub`.

#### Scenario: Publish
- **WHEN** a user with a full name saves a valid handle with `published: true`
- **THEN** the response is 200, and `GET /api/public/cv/<handle>` without a token answers 200 with their CV

#### Scenario: Pause and resume
- **WHEN** the owner saves `published: false`, and later `published: true`
- **THEN** the page answers 404 while paused and 200 again afterwards with the same handle and settings

#### Scenario: Remove
- **WHEN** the owner calls `DELETE /api/cv/public`
- **THEN** the response is 204, the page answers 404, `GET /api/cv/public` says `configured: false`, and the handle is held for 90 days

#### Scenario: No name
- **WHEN** the profile has no full name and the user publishes
- **THEN** the response is 409 `name_required`

#### Scenario: Remove while locked
- **WHEN** a page locked by an administrator is removed by its owner
- **THEN** the response is 409 `locked` and the page and its lock stay

#### Scenario: Another user's page is unreachable
- **WHEN** users A and B each published and A calls the management endpoints
- **THEN** A sees and changes only A's page

#### Scenario: Anonymous management call
- **WHEN** any `/api/cv/public` management endpoint is called without a valid token
- **THEN** the response is 401

### Requirement: The public page is live
`GET /api/public/cv/{handle}` SHALL need no token and SHALL answer with a standalone HTML page rendered from the owner's **current saved profile** and the saved settings. A saved change of the profile or of the settings SHALL show on the next request. Unsaved edits SHALL NOT. The page SHALL be cached in memory per profile version and settings version, never across versions.

#### Scenario: Anonymous read
- **WHEN** an anonymous client requests the page of a published handle
- **THEN** the response is 200 `text/html; charset=utf-8` with the owner's name

#### Scenario: Live edit
- **WHEN** the owner saves a changed summary in the profile and the summary section is public
- **THEN** the next request shows the new summary

#### Scenario: Unticked edit stays private
- **WHEN** the owner changes the phone number and the phone is not public
- **THEN** the page does not contain it

#### Scenario: Not found is uniform
- **WHEN** the handle is unknown, paused, removed, locked, malformed, or its owner has no full name
- **THEN** the response is the same 404 with the body `{"error":"not_found"}` in every case

#### Scenario: Account deleted
- **WHEN** the owner's user row is deleted
- **THEN** the page answers 404, and the handle, settings and audit rows are gone

#### Scenario: No other public path
- **WHEN** an anonymous client requests `/api/public/` or `/api/public/cv/` or `/api/public/other`
- **THEN** the response is 401 or 404 and no content is returned

### Requirement: Safe headers and content
The page SHALL carry `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; sandbox`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-cache`, an `ETag` (and answer 304 to a matching `If-None-Match`) and `X-Robots-Tag: noindex, nofollow, noarchive, nosnippet` unless the owner set `indexable`, in which case `index, follow`. It SHALL contain no script. Every profile string SHALL be escaped; links SHALL be emitted only for `http` and `https` addresses with `rel="noopener noreferrer nofollow"`.

#### Scenario: Headers
- **WHEN** the page is requested
- **THEN** each of those headers is present with those values

#### Scenario: Indexable opt-in
- **WHEN** the owner sets `indexable: true`
- **THEN** `X-Robots-Tag` is `index, follow` and the meta robots tag agrees

#### Scenario: Markup is text
- **WHEN** an achievement is `<script>alert(1)</script> "x" & y`
- **THEN** the response holds `&lt;script&gt;` and no `<script` tag

#### Scenario: Conditional request
- **WHEN** a client repeats the request with the `ETag` it received
- **THEN** the response is 304 with no body, and after a profile change it is 200 with a new `ETag`

### Requirement: Rate limits
The page SHALL allow at most 60 requests per minute per client IP, at most 10 per minute per IP for answers that are 404, and at most 600 per minute per handle in total, answering 429 `{"error":"rate_limited"}` with `Retry-After`. The client IP SHALL be the first address of `X-Forwarded-For` when present, otherwise the socket address. Management endpoints SHALL allow 60 handle checks and 30 previews per user per hour (429 beyond).

#### Scenario: Per-IP limit
- **WHEN** one IP requests a published page 61 times in a minute
- **THEN** the 61st answers 429 with `Retry-After`

#### Scenario: Guessing is slowed
- **WHEN** one IP requests 11 unknown handles in a minute
- **THEN** the 11th answers 429

#### Scenario: Per-handle cap
- **WHEN** a page receives 601 requests in a minute from many different IPs
- **THEN** the 601st answers 429 whatever its IP

#### Scenario: Other users unaffected
- **WHEN** one IP is limited
- **THEN** other IPs still get the page

### Requirement: Preview for the owner
`POST /api/cv/public/preview` with a settings object SHALL return the path of a one-use ticket valid for 60 seconds. `GET /api/cv/public/preview/{ticket}` SHALL need no token, answer once with the page as it would be with those settings, rendered from the owner's profile at that moment, with the page's headers except `Cache-Control: no-store` and `X-Robots-Tag: noindex`, and afterwards answer 404. A ticket SHALL work only for the settings and owner it was made for.

#### Scenario: Preview once
- **WHEN** the owner posts settings and opens the returned path
- **THEN** the response is 200 with the page, and a second request to the same path is 404

#### Scenario: Expiry
- **WHEN** a ticket is opened more than 60 seconds after it was made
- **THEN** the response is 404

#### Scenario: Preview needs no published page
- **WHEN** the owner has never published
- **THEN** the preview still works

#### Scenario: Forged ticket
- **WHEN** a request uses an invented ticket
- **THEN** the response is 404

### Requirement: Administrator takedown
`DELETE /api/admin/public-cv/{handle}` SHALL pause the page and lock it so that its owner cannot publish again, and `POST /api/admin/public-cv/{handle}/unlock` SHALL lift the lock. Both SHALL need the ADMIN role and answer 204, or 404 for an unknown handle.

#### Scenario: Takedown
- **WHEN** an administrator takes down a published handle
- **THEN** the page answers 404, the owner's `GET /api/cv/public` shows `locked: true`, and publishing answers 409 `locked`

#### Scenario: Unlock
- **WHEN** an administrator unlocks it
- **THEN** the owner can publish again

#### Scenario: Not an admin
- **WHEN** a user without the ADMIN role calls either endpoint
- **THEN** the response is 403

### Requirement: Audit and privacy
Every publish, pause, settings change, handle change, removal, takedown and unlock SHALL be recorded with its time and, for settings, which flags were on (never their values). The system SHALL NOT write the handle together with an IP, profile text, contact data or settings values to logs, exception messages or events, and SHALL NOT keep a view counter.

#### Scenario: Audit entries
- **WHEN** the owner publishes, changes the handle and pauses
- **THEN** three audit entries exist with their times and the flags that were on at publish

#### Scenario: Nothing in logs
- **WHEN** a page is requested, rejected, limited or previewed
- **THEN** the captured application log contains no handle, email, phone or profile text

### Requirement: Review fixes
The render cache SHALL never serve a page built from older settings or an older profile, an owner who removes a page and claims a different handle SHALL be bound by the 30-day rule, and saves and removals SHALL be rate limited per user.

#### Scenario: Cache never shows older settings
- **WHEN** a page with the email on has been requested, then the owner removes it and saves again with the email off (or saves twice, or edits the profile)
- **THEN** the next request of the page does not contain the email

#### Scenario: Reclaim after remove
- **WHEN** an owner removes the page and, less than 30 days after the last handle claim, claims a different handle
- **THEN** the answer is 409 `handle_change_too_soon`; claiming the same handle again, or any handle after 30 days, is allowed; the first claim is always allowed

#### Scenario: Save rate limit
- **WHEN** one user sends more than 30 saves and removals in an hour
- **THEN** the next one is 429 `rate_limited` with `Retry-After`; conflict answers such as `handle_taken` are unchanged
