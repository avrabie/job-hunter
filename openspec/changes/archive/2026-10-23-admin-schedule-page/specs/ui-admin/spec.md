# Spec Delta

## Purpose
Defines the administrator page of the web UI, where a user with the ADMIN role reads and changes the shared crawl schedule using their own session.

## ADDED Requirements

### Requirement: Admin entry point
The signed-in shell SHALL show an "Admin" link to `/admin` only when the current access token lists the `ADMIN` role. This is a display decision; the backend remains the only enforcement.

#### Scenario: Administrator
- **WHEN** a user whose access token has the ADMIN role is signed in
- **THEN** the shell shows the "Admin" link

#### Scenario: Ordinary user
- **WHEN** a signed-in user's token has no ADMIN role
- **THEN** the shell shows no "Admin" link

### Requirement: Schedule display
The page at `/admin` SHALL load `GET /api/admin/crawl-schedule` through the API client and show whether crawling is enabled, the cron expression, the time zone, the next run (when enabled) and the time of the last change, with a loading state and an error state with retry. A visitor who is not signed in SHALL see the landing page at that address.

#### Scenario: Enabled schedule
- **WHEN** an administrator opens `/admin` and the schedule is enabled
- **THEN** the cron, time zone and next run are shown

#### Scenario: Disabled schedule
- **WHEN** the schedule is disabled
- **THEN** the page says crawling is off and shows no next run

#### Scenario: Not an administrator
- **WHEN** the API answers 403
- **THEN** a message says the page is for administrators and no form is shown

#### Scenario: Signed out
- **WHEN** a visitor without a session opens `/admin`
- **THEN** the landing page is shown and nothing is requested from the API

### Requirement: Schedule change
The page SHALL let an administrator set enabled, cron and time zone and save them with `POST /api/admin/crawl-schedule`. After a successful save the page SHALL show the stored values and next run from the answer. A rejected change (400) SHALL show the server's field and message and keep what the user typed. Saving SHALL be disabled while a request is running.

#### Scenario: Save
- **WHEN** an administrator changes the cron and saves
- **THEN** the stored schedule and its next run are shown

#### Scenario: Rejected
- **WHEN** the API answers 400 for a cron that is too frequent
- **THEN** the message is shown next to the form and the typed values stay

#### Scenario: No token leaks
- **WHEN** the page loads or saves
- **THEN** no token is written to the console or to storage
