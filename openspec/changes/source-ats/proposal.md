# Proposal

## Why

The shared `postings` store is live but empty. The first source we looked at, SwissDevJobs, forbids automated access and reuse of its ads in its terms, so we do not use it. Applicant-tracking systems publish their customers' jobs through documented public APIs meant to be read by programs, so a list of Swiss tech employers on those systems is a source we can use without breaking anyone's rules.

## What Changes

- Add the first source adapter, for **Greenhouse** job boards, in the `sources` module. For each configured board it reads the public job list, keeps Swiss IT roles, reads each kept job's detail, maps it to a `PostingCandidate` and hands it to `PostingIngest`.
- Add a configurable list of boards (Greenhouse board tokens). The seed list holds boards checked on 2026-10-22 to answer and to have Swiss roles: `proton`, `getyourguide`, `scandit`, `databricks`, `elastic`, `mongodb`, `gitlab`, `datadog`, `grafanalabs`, `ripple`.
- Filter by place and role: a job is kept when its location text names Switzerland or a Swiss city, and its title looks like an IT role (a configurable keyword list). Everything else is counted as filtered out and not stored.
- Map what the API gives: title, company name, plain-text description from the job HTML, the job URL, the first Swiss city and its canton, and the item JSON as the raw payload. Salary, workload and remote mode stay unknown.
- Fetch politely, as every adapter must: identifying User-Agent, robots.txt check, timeouts, size caps, sequential requests with a pause, conditional GET on the board list (`ETag`), a minimum interval per board, and no overlapping runs.
- Keep per-board fetch state (ETag, last run time) in a new table so these rules survive restarts.
- Isolate failures: one board or job failing does not stop the others, and every run returns counts per board.
- Add a public `sources` API to start a crawl (`AtsCrawl`) and an opt-in, default-off run at application start, so the first real crawl can be checked on s4v3.net before the scheduler exists.

### Observed about the API (checked 2026-10-22)

- `GET https://boards-api.greenhouse.io/v1/boards/{token}/jobs` needs no authentication ("publicly available" in Greenhouse's docs), answers with an `ETag`, and answers `304` to a conditional request. `GET .../jobs/{id}` returns one job with its full HTML content, departments and offices. The host's `robots.txt` disallows only `/embed/`.
- The list with `content=true` is large (9.9 MB for Databricks' 899 jobs), so the adapter reads the light list first and fetches details only for the kept jobs.
- A board covers a whole company: Databricks has 9 Swiss roles out of 899, Proton 24 of 33, GetYourGuide 6 of 74.
- Greenhouse's documentation says nothing about reuse of the data by third parties. The data is each employer's own job ad, published to be found and applied to, and the API is the documented way to read it. We store the text so users can be matched and then link to the original ad for applying.
- Location is free text (`Zurich;`, `Geneva; Taipei; Skopje`, `Zürich, Switzerland`). A job may list several places, and the first Swiss one is used.
- Departments are company-specific (`CC - Infrastructure - Storage`, `Field Engineering`), so they cannot tell IT from non-IT; the title is used instead.

### Out of scope

- Other systems (Lever, Ashby, Personio, SmartRecruiters, Workable). Lever's documentation explicitly allows reading published postings, but none of the Lever boards tried had Swiss roles; it follows once there is a Swiss employer to test with.
- SwissDevJobs, jobs.ch and other boards (terms or robots.txt rule them out).
- Scheduling and repeated crawls (`ingestion-scheduler`).
- Salary (Greenhouse offers it only through an extra request per job and few employers publish it), workload and remote mode.
- Expiry or closing of postings that leave a board.
- Finding new boards automatically. The list is maintained by hand.
- Any HTTP trigger endpoint: nothing is exposed over HTTP.
- Matching, scoring or any use of the postings.

## Capabilities

### New Capabilities
- `sources`: how source adapters fetch and hand over postings (politeness, robots.txt, conditional fetch, failure isolation, run results), and the Greenhouse adapter's filtering and mapping.

### Modified Capabilities

None. `postings` is used as it is. `module-boundaries` already lists `sources`.

## Impact

- **Modules:** `sources` (new code, first dependency on `postings` through its public API). No change to `postings`.
- **Database:** one new table, `source_fetch_state`, in a new changeset file `004-sources.sql` (changeset `007`).
- **Dependencies:** none expected; JSON parsing uses the Jackson already present, HTTP uses Spring's `RestClient`.
- **Network:** new outbound HTTPS from the backend pod to `boards-api.greenhouse.io`. The cluster's network policies restrict only ingress, so no policy change.
- **Personal data:** none by design; postings are public ads. Recruiter or contact names inside ad text stay in the stored text and are never logged.
- **Per-user scoping:** none; postings are shared.
- **Claude cost:** none.
- **Replaces:** the active change `source-swissdevjobs`, which stopped at its terms check. Its generic parts (politeness, robots.txt, conditional fetch state, run results, no-overlap lock, opt-in startup run) carry over here; it must be discarded before this change is synced, because both create the `sources` capability.
