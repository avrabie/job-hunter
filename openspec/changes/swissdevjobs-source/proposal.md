## Why

Phase 2 (ingestion) has one source, Greenhouse boards of a few international employers. The product is for the Swiss market, and SwissDevJobs is a Swiss board that lists only Swiss IT jobs, mostly with a salary. It publishes a JSON list at `/api/jobsLight` and an RSS feed with each job's full text, and its `robots.txt` allows both for all crawlers. jobs.ch and jobup.ch disallow `/api/` and their job pages in `robots.txt`, and Job-Room offers no public read API, so they are not crawled (see design).

## What Changes

- A SwissDevJobs adapter in `sources`: reads the JSON list for structured data (title, company, city, workplace, salary, technologies) and the RSS feed for the job description, joins them by the job's slug, and hands each job to `postings`.
- A crawl run now covers Greenhouse boards and SwissDevJobs; one failing does not stop the other. The scheduler, the startup run and the admin schedule API are unchanged.
- Setting `jobhunter.sources.swissdevjobs.*` (on by default; can be switched off).
- Jobs without a description in the feed (partner ads) are skipped and retried on the next run.

Out of scope: jobs.ch, jobup.ch, Job-Room (not allowed or not available), removing postings that disappear from the board, a UI change, matching or scoring.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `sources`: adds the SwissDevJobs adapter and the combined run.

## Impact

- Backend only: `sources` module (new adapter, a combined crawl entry), `SourcesSettings`, `application.yaml`, README of `sources`, PLAN.md.
- No schema change: fetch state and postings tables already take a new source id.
- Outbound HTTPS to `swissdevjobs.ch` (already allowed: egress is public HTTPS on 443).
- Roughly 235 postings on the first run, one list request, one feed request and the robots.txt read per run.
