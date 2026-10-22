# Spec Delta

## ADDED Requirements

### Requirement: Scheduled crawl
When the schedule is enabled, the application SHALL start a crawl of all configured boards at each time given by the schedule's cron expression, evaluated in the schedule's time zone. The initial schedule SHALL come from settings, with the defaults `0 0 * * * *` (every hour) in `Europe/Zurich`, and SHALL be off by default. A schedule stored through the API SHALL take precedence over the settings.

#### Scenario: Default
- **WHEN** the application starts with default settings and no stored schedule
- **THEN** no crawl is scheduled and no request is made to any board

#### Scenario: Enabled with a schedule
- **WHEN** the schedule is enabled and its cron expression matches the current time
- **THEN** one crawl is started and its per-board outcomes are logged

#### Scenario: Default times
- **WHEN** the schedule is enabled with default settings
- **THEN** crawls are due at the start of every hour in the Europe/Zurich time zone

#### Scenario: Stored schedule wins
- **WHEN** a schedule has been stored through the API and the settings say something else
- **THEN** the stored schedule is used, also after a restart

### Requirement: Invalid initial schedule stops startup
An invalid cron expression or time zone in the initial settings SHALL stop the application at startup with an error that names the setting, but only when the settings enable the schedule and no schedule is stored.

#### Scenario: Bad cron
- **WHEN** the settings enable the schedule and the cron is `not a cron`
- **THEN** startup fails with an error naming the cron setting

#### Scenario: Bad time zone
- **WHEN** the settings enable the schedule and the time zone is `Mars/Base`
- **THEN** startup fails with an error naming the time zone setting

#### Scenario: Disabled schedule is not validated
- **WHEN** the settings disable the schedule and the cron is `not a cron`
- **THEN** the application starts

### Requirement: Schedule API
The system SHALL let an administrator read and change the crawl schedule. `GET /api/admin/crawl-schedule` SHALL answer the current schedule (`enabled`, `cron`, `zone`, `nextFire`, and when it was last changed). `POST /api/admin/crawl-schedule` with `enabled`, `cron` and `zone` SHALL validate and store the schedule and answer it. Both endpoints SHALL require a valid access token with the `ADMIN` role. A stored schedule SHALL take effect without a restart, within one minute.

#### Scenario: Read the schedule
- **WHEN** an administrator sends GET
- **THEN** the answer is 200 with the current `enabled`, `cron`, `zone` and the next fire time (absent when disabled)

#### Scenario: Change the schedule
- **WHEN** an administrator posts `{"enabled":true,"cron":"0 30 6,14 * * *","zone":"Europe/Zurich"}`
- **THEN** the answer is 200 with that schedule and its next fire time, and a later GET returns the same

#### Scenario: Takes effect without restart
- **WHEN** an administrator stores a schedule that matches the next minute
- **THEN** a crawl starts within about two minutes without a restart

#### Scenario: Survives a restart
- **WHEN** a schedule was stored and the application restarts
- **THEN** a GET answers the stored schedule

#### Scenario: Disable
- **WHEN** an administrator posts `enabled: false`
- **THEN** no further crawl is started and the next fire time is absent

#### Scenario: Not signed in
- **WHEN** a request has no token
- **THEN** the answer is 401 and nothing changes

#### Scenario: Not an administrator
- **WHEN** a user without the `ADMIN` role sends GET or POST
- **THEN** the answer is 403 and the stored schedule is unchanged

### Requirement: Schedule validation
The API SHALL reject, with a 400 that names the offending field and stores nothing, a cron expression that is invalid, a time zone that is unknown, a missing field, and a cron whose consecutive fire times are closer than the minimum interval between runs of a board (15 minutes by default).

#### Scenario: Bad cron
- **WHEN** the cron is `not a cron`
- **THEN** the answer is 400 naming `cron` and the stored schedule is unchanged

#### Scenario: Bad zone
- **WHEN** the zone is `Mars/Base`
- **THEN** the answer is 400 naming `zone`

#### Scenario: Too frequent
- **WHEN** the cron is `0 * * * * *` (every minute)
- **THEN** the answer is 400 naming `cron` and saying the minimum gap

#### Scenario: Missing field
- **WHEN** the body has no `cron`
- **THEN** the answer is 400 naming `cron`

### Requirement: Schedule changes are traceable without personal data
A stored schedule SHALL record when it changed and the `sub` of the administrator who changed it, and SHALL NOT record an email or name. The log line for a change SHALL hold the new `enabled` and `cron` values and no identity.

#### Scenario: Recorded change
- **WHEN** an administrator changes the schedule
- **THEN** the stored row holds the time and the administrator's `sub`, and the log line holds `enabled` and `cron` only

### Requirement: Scheduled runs do not overlap
A scheduled crawl SHALL NOT start while an earlier scheduled crawl in the same process is still running. The skipped run SHALL be logged and not queued.

#### Scenario: Previous run still going
- **WHEN** the schedule fires while the previous crawl has not finished
- **THEN** no second crawl starts and the skip is logged

#### Scenario: Next run after a finished one
- **WHEN** the schedule fires after the previous crawl finished
- **THEN** a new crawl starts

### Requirement: A failed scheduled run does not stop the schedule
If a scheduled crawl throws, the failure SHALL be logged without response or posting content, and the schedule SHALL keep firing.

#### Scenario: Crawl throws
- **WHEN** a scheduled crawl throws an exception
- **THEN** the failure is logged with the exception type only, and the next scheduled time starts a new crawl

### Requirement: No catch-up of missed runs
A crawl that was due while the application was not running, or before a schedule change, SHALL NOT be started afterwards. The next scheduled time runs normally.

#### Scenario: Pod down at the scheduled time
- **WHEN** the application starts at 07:30 after being down at 07:00
- **THEN** no crawl starts because of the missed time, and the next crawl starts at 08:00

#### Scenario: Schedule changed
- **WHEN** an administrator stores a new schedule at 07:20
- **THEN** the next fire time is computed from 07:20 under the new schedule, with no catch-up

### Requirement: Scheduled run summary in the log
Each scheduled crawl SHALL log one line when it starts and one line when it ends. The end line SHALL hold the number of boards per outcome and the total of created, updated, unchanged, skipped and filtered-out jobs, and SHALL NOT hold job titles, descriptions or response bodies.

#### Scenario: Summary line
- **WHEN** a scheduled crawl finishes with 9 completed boards and 1 failed board
- **THEN** the end line states those board counts and the job totals, and contains no job text
