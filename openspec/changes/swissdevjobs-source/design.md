## Context

`sources` has one adapter (`GreenhouseCrawl`) behind the public `AtsCrawl.run()`, which `CrawlScheduler` and `StartupCrawl` call. `PoliteHttp`, `RobotsTxt` and `FetchStateStore` are reusable. `postings` takes a `PostingCandidate` and does dedupe, language detection and the `JobIngested` event.

Checked on 2026-10-26 against the live site, from the public endpoints only:
- `GET /api/jobsLight` returns about 235 live jobs as JSON in one call: `jobUrl` (slug), `name`, `company`, `actualCity`/`cityCategory`, `workplace` (remote/hybrid/office), `jobType`, `expLevel`, `language`, `technologies`, `annualSalaryFrom/To`, `isPaused`, `isPartner`, `activeFrom`, `redirectJobUrl`. It has no description text.
- `GET /rss` is one XML file (about 1.7 MB, about 230 items). Each item has the link `https://swissdevjobs.ch/jobs/<slug>?utm_...` and the full description as HTML (salary, requirements, responsibilities, technologies, free text).
- Joining by slug matched 225 of 235 list entries; the 10 without a match include the partner ads.
- `robots.txt` for `User-agent: *` disallows only some static paths and `*.txt$`; `/api/` is disallowed only for Meta's crawler. A per-job JSON endpoint does not exist (`/api/jobs/<slug>` returns the HTML app).
- jobs.ch and jobup.ch: `Disallow: /api/`, `/api_proxy/` and the job detail paths for everyone. Job-Room: no public read API found. Not crawled. The owner decided against an exception.

## Goals / Non-Goals

**Goals**
- All Swiss IT jobs of SwissDevJobs in `postings` with salary, remote mode and description, idempotently, politely and in the existing schedule.

**Non-Goals**
- Other boards; removal of closed jobs; storing the employer's apply link; a role or location filter (every listing is Swiss IT).

## Decisions

**Two requests, joined by slug.** The list gives structured fields, the feed gives the text. Both are single requests, so politeness cost is two requests plus `robots.txt` per run. A list entry without a feed item has no text and is skipped, counted as skipped, and tried again next run (the run does not keep a validator that would hide it).

**Canonical URL is the SwissDevJobs page** (`https://swissdevjobs.ch/jobs/<slug>`, no tracking query), as the posting URL. The employer's apply link (`redirectJobUrl`) is not stored; nothing is sent to employers.

**One entry point for several adapters.** `AtsCrawl` is injected by the scheduler and the startup run. A small combined crawl implements it and runs the adapters in order, collecting every `BoardResult`; the Greenhouse adapter stays as it is. `BoardResult.board` is `swissdevjobs` for the new one. A failure in one adapter becomes a failed result for that adapter only.

**Fetch state.** Source id `swissdevjobs` in `source_fetch_state`: the minimum interval applies as before. If the list answers with an `ETag`, it is stored and sent back, as for Greenhouse; the feed is always fetched together with the list. When the list answers 304 the run ends as not modified without requesting the feed. Unchanged content is anyway cheap: ingest compares the content hash.

**Mapping.**
- title ← `name`; company ← `company`; city ← `actualCity` (falling back to `cityCategory`); canton ← the same lookup Greenhouse uses when the city is known, else null.
- remote mode ← `workplace`: remote→REMOTE, hybrid→HYBRID, otherwise ONSITE; unknown→null.
- salary ← `annualSalaryFrom/To` as yearly CHF (the board states CHF), both null if one is missing or not positive.
- workload: not given by the list as a percentage; null (the slug and text may name one, not parsed here).
- description ← the feed item's HTML converted to plain text with the same conversion as Greenhouse text; raw payload ← the list entry JSON plus the feed item's description, unchanged.
- Skipped when `isPaused` is true, or title/company/description is blank.

**Feed parsing.** The feed is untrusted XML: parsed with external entities and DTDs disabled, with a size cap (the list cap, 8 MB), and only `link`, `description` and `title` of each item are read.

**Settings.** `jobhunter.sources.swissdevjobs.enabled` (default true), `.baseUrl` (default `https://swissdevjobs.ch`, so tests use a fake server).

## Risks / Trade-offs

- Undocumented endpoint: `/api/jobsLight` is the site's own API and may change shape. Malformed lists fail the run cleanly with `malformed list`; nothing partial is ingested.
- No explicit terms found for reuse (the site has no terms page). Mitigation: robots.txt honoured, identifying User-Agent, two requests per run, postings link back to the source page, no republishing. Courtesy contact is the owner's choice.
- Partner ads (about 5 of 235) have no text in the feed and are skipped.
- Closed jobs stay in `postings`; the same as Greenhouse today.

## Migration Plan

No schema change. Deploy the backend; the next scheduled run (or an admin schedule change) ingests the first ~230 postings. Rollback: set `enabled` to false or redeploy the previous tag.

## Open Questions

- None blocking. Whether the apply link should be shown later (UI) is a separate change.
