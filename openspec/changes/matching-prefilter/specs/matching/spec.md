# Spec Delta

## Purpose

Decides, for each user, which shared postings are worth a closer look: a deterministic prefilter with hard rules and a relevance score, stored per user and posting, kept current when postings arrive or the profile changes, and readable by the owner only.

## ADDED Requirements

### Requirement: Hard rules
The prefilter SHALL reject a posting for a user (`passed = false`) when a hard rule fails: out of scope language, canton, workload, remote mode or minimum salary, each defined by its scenario below. A rule whose posting value is unknown, or whose user preference is empty, SHALL NOT fail.

#### Scenario: Wrong canton
- **WHEN** the user prefers cantons ZH and ZG and the posting is in GE
- **THEN** the posting does not pass and the reasons include `CANTON_MISMATCH`

#### Scenario: Unknown canton passes the rule
- **WHEN** the user prefers canton ZH and the posting has no canton
- **THEN** the canton rule does not fail and the reasons include `UNKNOWN_CANTON`

#### Scenario: No preference
- **WHEN** the user lists no cantons
- **THEN** the canton rule never fails and no canton reason is recorded

#### Scenario: Workload ranges
- **WHEN** the user wants 60-80% and a posting asks for 80-100%
- **THEN** the ranges overlap at 80 and the workload rule does not fail
- **AND WHEN** the posting asks for 100% only
- **THEN** the posting does not pass and the reasons include `WORKLOAD_MISMATCH`

#### Scenario: Remote mode
- **WHEN** the user accepts only `REMOTE` and the posting is `ONSITE`
- **THEN** the posting does not pass and the reasons include `REMOTE_MISMATCH`

#### Scenario: Salary below minimum
- **WHEN** the user's minimum is 120000 and the posting's maximum salary is 100000
- **THEN** the posting does not pass and the reasons include `SALARY_BELOW_MINIMUM`

#### Scenario: Out of scope language
- **WHEN** a posting is flagged out of scope
- **THEN** it does not pass for any user and the reasons include `OUT_OF_SCOPE`

#### Scenario: Work authorisation is not used
- **WHEN** the user's work authorisation is `NEEDS_SPONSORSHIP`
- **THEN** no hard rule fails because of it

### Requirement: Reason codes
Every result SHALL record fixed reason codes and no free text. A failed hard rule is recorded as `OUT_OF_SCOPE`, `CANTON_MISMATCH`, `WORKLOAD_MISMATCH`, `REMOTE_MISMATCH` or `SALARY_BELOW_MINIMUM`; a rule skipped because the posting value is unknown as `UNKNOWN_CANTON`, `UNKNOWN_WORKLOAD`, `UNKNOWN_REMOTE` or `UNKNOWN_SALARY`; score parts as `ROLE_MATCH`, `SKILL_MATCH_<n>` and `LANGUAGE_MATCH`; a score under the threshold as `BELOW_THRESHOLD`.

#### Scenario: Codes only
- **WHEN** a posting is rejected for two rules
- **THEN** the result lists exactly those two codes and no text from the profile or the posting

### Requirement: Relevance score
The prefilter SHALL compute an integer score from 0 to 100 as the sum of three parts: up to 50 for the posting title against the user's target roles (or, with no target roles, the user's job titles), up to 40 for the user's skills found in the posting text as whole words, and 10 for a posting language the user speaks at B2 or above. A posting SHALL pass when no hard rule fails and its score reaches the threshold (a setting, default 30).

#### Scenario: Title and skills
- **WHEN** the user targets "Backend Engineer" with skills Kotlin, PostgreSQL, Kubernetes, Docker and Spring, and a posting titled "Senior Backend Engineer" mentions all five skills in German and the user speaks German at C1
- **THEN** the score is 100 and the posting passes

#### Scenario: Partial credit
- **WHEN** a target role has four words and the title contains two of them, and three of the user's skills appear in the text
- **THEN** the title part is 25 and the skills part is 24

#### Scenario: No overlap
- **WHEN** a posting titled "Graphic Designer" mentions none of the user's skills
- **THEN** its score is below the threshold and it does not pass, with the reason `BELOW_THRESHOLD`

#### Scenario: Whole words only
- **WHEN** the user's skill is "Go" and a posting says "good" and "going" but never the word "Go"
- **THEN** the skill does not count

#### Scenario: Reasons are codes
- **WHEN** the score is computed
- **THEN** the recorded reasons are fixed codes such as `ROLE_MATCH`, `SKILL_MATCH_3` and `LANGUAGE_MATCH`, and no profile text or posting text is recorded

#### Scenario: Threshold is a setting
- **WHEN** the threshold is raised to 60
- **THEN** a posting with score 45 that passed at 30 does not pass at the next evaluation

### Requirement: Stored results
The system SHALL store one result per user and posting: whether it passed, the score, the reason codes, the version of the profile used and the time of evaluation. Evaluating the same pair again SHALL replace the stored result. Results SHALL be deleted with the user's account and SHALL NOT be stored for users without a stored profile.

#### Scenario: One row per pair
- **WHEN** the same posting is evaluated twice for the same user
- **THEN** one row exists and it holds the latest result

#### Scenario: Deleted with the account
- **WHEN** a user's record is deleted
- **THEN** that user's results are deleted too

#### Scenario: No profile
- **WHEN** a user has not saved a profile
- **THEN** no result is stored for that user

### Requirement: Evaluation when a posting arrives
When a `JobIngested` event is delivered, the system SHALL evaluate that posting for every user who has a stored profile. The listener SHALL be idempotent, so redelivery of the event leaves the same results, and a failure for one user SHALL NOT stop the others.

#### Scenario: New posting
- **WHEN** a posting is ingested and two users have profiles
- **THEN** each user has a result for it

#### Scenario: Redelivery
- **WHEN** the same `JobIngested` event is delivered twice
- **THEN** each user still has exactly one result for the posting

#### Scenario: One user fails
- **WHEN** evaluating one user's profile raises an error
- **THEN** the other users' results are still stored

### Requirement: Evaluation when the profile changes
When a `ProfileSaved` event is delivered, the system SHALL re-evaluate all postings for that user against the saved profile, replacing earlier results. Postings are read page by page.

#### Scenario: Profile saved
- **WHEN** a user saves a profile that now prefers canton GE
- **THEN** after the event is handled, every posting in another known canton does not pass for that user

#### Scenario: Other users unchanged
- **WHEN** one user saves a profile
- **THEN** no other user's results change

### Requirement: Matches endpoint
`GET /api/matches` SHALL return the calling user's passing results, best score first and ties by newest posting first, one page at a time (`page` from 0, `size` default 20 and at most 100). Each item SHALL hold the posting's id, title, company, city, canton, URL, the score and the reason codes. The endpoint SHALL require a valid access token and SHALL use only the token's `sub` to choose whose results are returned.

#### Scenario: Own matches only
- **WHEN** user A has two passing results and user B has three
- **THEN** A receives exactly A's two and B receives exactly B's three

#### Scenario: Ordering
- **WHEN** a user has passing results with scores 70, 90 and 70
- **THEN** the 90 comes first, and the two 70s are ordered by newest posting first

#### Scenario: Paging
- **WHEN** `size=1` is requested
- **THEN** one item is returned, and `page=1` returns the next one

#### Scenario: Size limit
- **WHEN** `size=1000` is requested
- **THEN** at most 100 items are returned

#### Scenario: Not signed in
- **WHEN** no token is sent
- **THEN** the answer is 401

#### Scenario: Failed results are not listed
- **WHEN** a user's result for a posting did not pass
- **THEN** it does not appear in the list

### Requirement: Personal data stays out of logs and responses
The matching module SHALL NOT log profile content, posting text, scores per user or reason codes together with a user id; logs MAY hold counts, durations and posting ids. Responses SHALL NOT include the profile.

#### Scenario: Evaluation logging
- **WHEN** a posting is evaluated for several users
- **THEN** the log lines contain no profile text, no skill or role names, and no user id next to a score
