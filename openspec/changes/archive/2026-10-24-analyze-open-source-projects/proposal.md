# Proposal

## Why

Picked open source repositories are only names and descriptions. The user wants each one analysed (what it is, how it is built, which technologies it uses) so the profile gains skills that are backed by real evidence, not typed by hand.

## What Changes

- New "Analyze" action on each saved open source project. It reads public GitHub data for that repository (README, file tree, dependency manifests), asks Claude for a summary, an architecture description and a tech stack, and returns an analysis. Nothing is stored by the call itself.
- The profile page puts the returned analysis on the project and **auto-adds the detected technologies to Skills** as unsaved changes. Each such skill carries its sources (repository, link and the file that shows it), so it is traceable. The user can remove any of it and presses Save to keep it (decided by the owner: no per-skill approval step).
- `OpenSourceProject` gains an optional `analysis`; `Skill` gains `origin` (`MANUAL` or `ANALYSIS`) and `sources`. Re-analysing a project replaces its earlier sources; removing a project removes its sources, and a skill left with no sources that came from analysis is removed with them.
- New endpoint `POST /api/profile/open-source-projects/analyze`, limited per user per hour.
- A technology is only kept when the model names a file that really exists in the repository's tree; an answer cannot cite a file it was not shown.

## Capabilities

### New Capabilities
- `project-analysis`: the analyze endpoint, what is read from a repository, limits, failures and the rules that make skills traceable.

### Modified Capabilities
- `profile`: skill `origin` and `sources`, project `analysis`, limits.
- `llm`: typed project analysis from a repository digest.
- `ui-profile`: Analyze button, analysis display, skills with sources.

## Impact

- Modules: `profile` (model, validation, repository digest from GitHub), `llm` (analyst), `web` (endpoint, limiter), UI `profile` folder. No Liquibase change: the profile is a JSON document read leniently.
- Personal data and cost: repository text goes to Anthropic (public repositories only; the user chose them). One Claude call per analysis (Sonnet 5.5 class, bounded input of about 60 KB of text). Per-user limit, default 10 an hour, its own counter (a shared counter with CV extraction would need the CV module's limiter moved; see design).
- Per-user scoping: only a repository that is in the caller's saved profile can be analysed, so the endpoint cannot be used to run Claude on arbitrary repositories.
- Prompt injection: repository text is untrusted; the model has no tools and its answer is validated.
- Outbound calls: one GitHub API call (file tree) and up to ten file downloads from `raw.githubusercontent.com` per analysis.

## Out of scope

Private repositories and tokens, cloning, reading source files beyond manifests and the README, analysing on a schedule or on save, skill levels (analysis never sets a level), using skills for matching or CV drafting (that follows the existing rule that drafts only use profile facts), providers other than GitHub (the digest source sits behind the same provider contract).
