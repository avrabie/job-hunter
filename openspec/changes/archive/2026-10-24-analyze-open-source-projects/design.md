# Design

## Context

Open source projects are snapshots in the profile (`archive/2026-10-24-github-open-source-projects`). `RepositoryProvider` lists repositories; `KoogCvReader` shows the pattern for one structured Claude call (quoted data, no tools, manual JSON mode, fixed `LlmFailure`). `Skill(name, level)` is the only skill model. `llm` imports `profile` (for `Profile`), so `profile` cannot import `llm`; an orchestrator must live in a module that depends on both: `web`.

## Goals / Non-Goals

**Goals:** a bounded, evidence-checked analysis; skills that say where they came from; no new module or dependency.
**Non-Goals:** see proposal.

## Decisions

1. **Digest from GitHub, no clone.** `RepositoryDigestSource` (public interface in `profile`) with `digest(provider, fullName): DigestResult` (`Found(RepositoryDigest)`, `NotFound`, `RateLimited`, `Unavailable`, `InvalidName`). The GitHub implementation makes one API call, `GET /repos/{owner}/{repo}/git/trees/HEAD?recursive=1`, and downloads files from `https://raw.githubusercontent.com/{owner}/{repo}/HEAD/{path}` (raw downloads are not charged to the 60 an hour API limit, so one analysis costs one API call, not eleven). Files read: the README (first match of `README*` at the root, at most 20 KB) and up to 8 manifests chosen from the tree by a fixed filename list (`build.gradle(.kts)`, `settings.gradle(.kts)`, `pom.xml`, `package.json`, `requirements.txt`, `pyproject.toml`, `Cargo.toml`, `go.mod`, `Gemfile`, `composer.json`, `Dockerfile`, `docker-compose.yml`), shallowest first, at most 12 KB each; a tree path list capped at 400 entries (directories and files, depth at most 3). Base URLs come from configuration, never from the request; owner and repo are validated (`[A-Za-z0-9._-]`, 1 to 100) before use. Redirects off, body caps, timeouts as the listing provider. A truncated tree is flagged in the digest.
2. **Only saved projects.** The endpoint takes `{provider, fullName}` and loads the caller's profile through the `profile` public API; it analyses only if that pair is among the saved `openSourceProjects` (else 404 `project_not_found`). Alternative: accept any repo — rejected: free Claude calls for anyone signed in.
3. **Analyst in `llm`.** `ProjectAnalyst.analyze(digest: RepositoryDigest): ProjectAnalysis`; `KoogProjectAnalyst` copies `KoogCvReader` (manual JSON mode, repair model, fixed `LlmFailure`, no logging of content). Digest text is wrapped in `<repository>` tags with the closing tag stripped; the system prompt says it is data, to use only what the files show, never to guess, and to cite for each technology the path of one file shown. `ProjectAnalysis` and its parts are public data classes in `profile` (they are stored there), mapped by `AnalysisMapper` in `llm/internal`.
4. **Evidence check in the mapper.** A technology is kept only if its `evidencePath` is a path of the digest's tree (or the README), its name is 1 to 60 characters after trimming, and it is not a duplicate by case; at most 20 kept; unknown category becomes `OTHER`; summary and architecture are cut at their limits at a sentence boundary when possible and an empty summary fails the call as unusable.
5. **Orchestration in `web`.** `ProjectAnalysisController` (copy `CvExtractController` style): auth, load profile, check project, limiter, digest, analyst, map failures to fixed bodies. Limiter: `AnalysisLimiter` in `web/internal`, in memory, copy of `ExtractionLimiter`, `jobhunter.analysis.per-hour` default 10. The owner asked for the CV reader's limit; sharing the counter would mean moving `ExtractionLimiter` out of `cv/internal` into a public type of `llm` and touching its tests, which is a refactor with no user-visible gain, so this change keeps a second counter with the same behaviour. A shared counter can follow.
6. **Skills traceable by data.** `Skill` gains `origin` (`MANUAL` default, `ANALYSIS`) and `sources: List<SkillSource>` (`provider`, `fullName`, `url`, `evidence` = the cited path plus a short note, at most 300 characters; at most 20 per skill). The server stores what the client sends; it checks shape only. The client does the merging (a pure function in `ui/src/profile/skills.ts`, unit tested): matching by case-insensitive name; a match gets the source appended and keeps its origin; no match adds `{name, level: null, origin: ANALYSIS, sources:[…]}`. On re-analysis the repository's earlier sources are removed first; removing a project or its analysis removes its sources; an `ANALYSIS` skill left with no sources is removed.
7. **Stored analysis.** `OpenSourceProject.analysis: ProjectAnalysis?` with `summary`, `architecture`, `techStack`, `files` (paths read), `analyzedAt`. The client sets it on the picked project as an unsaved change; a save stores it.
8. **Limits.** summary 1500, architecture 2500, 20 technologies, 8 manifests plus README in `files`, skills limit 100 → 200, request body stays 256 KB.

## Contract (do not change)

Request: `POST /api/profile/open-source-projects/analyze` body `{"provider":"GITHUB","fullName":"octocat/hello-world"}`.
Response 200: `{"analysis":{"summary":"…","architecture":"…","techStack":[{"name":"Spring Boot","category":"FRAMEWORK","evidencePath":"build.gradle.kts","evidence":"spring-boot gradle plugin and starter dependencies"}],"files":["README.md","build.gradle.kts"],"truncated":false,"analyzedAt":"2026-10-24T12:00:00Z"}}`. Categories: `LANGUAGE, FRAMEWORK, LIBRARY, DATABASE, TOOL, PLATFORM, OTHER`.
Errors (fixed bodies `{"error":"<code>"}`): 400 `invalid_request`, 404 `project_not_found`, 404 `repository_not_found`, 429 `too_many_analyses`, 429 `provider_rate_limited`, 502 `provider_unavailable`, 502 `analysis_failed`, 503 `analysis_not_configured`.
Skill JSON: `{"name":"Spring Boot","level":null,"origin":"ANALYSIS","sources":[{"provider":"GITHUB","fullName":"octocat/hello-world","url":"https://github.com/octocat/hello-world","evidence":"build.gradle.kts: spring-boot gradle plugin and starter dependencies"}]}`.
Stored project gains `"analysis":{ "summary","architecture","techStack":[{"name","category","evidencePath","evidence"}],"files":[…],"truncated":false,"analyzedAt":"…"}` or null.
Kotlin types (package `net.s4v3.backend.profile`): `enum TechCategory`, `data class Technology(name, category, evidencePath, evidence)`, `data class ProjectAnalysis(summary, architecture, techStack, files, truncated, analyzedAt: Instant)`, `enum SkillOrigin { MANUAL, ANALYSIS }`, `data class SkillSource(provider: CodeProvider, fullName, url, evidence)`, `Skill(name, level = null, origin = MANUAL, sources = emptyList())`, `data class RepositoryDigest(provider, fullName, readme: String?, paths: List<String>, files: Map<String, String>, truncated: Boolean)`, `interface RepositoryDigestSource`, `sealed interface DigestResult`. In `llm`: `interface ProjectAnalyst { fun analyze(digest: RepositoryDigest): ProjectAnalysis }`.

## Risks / Trade-offs

- [Prompt injection from README text] → quoted data, no tools, evidence check, human can remove skills; the skills are claims about the repo, not about the user's level.
- [Auto-added skills overstate the user's skill, e.g. a library only listed in a manifest] → origin and sources are visible in the UI ("from repository analysis"), no level is set, and the project rule that drafts use only profile facts now includes these; flagged for the owner.
- [Someone else's repository saved under the user's account name] → listing only reads the user's own account (`type=owner`), analysis only runs on saved ones; a user can still type any username, which is already the case.
- [Cost] → per-user limit, bounded input, one call.
- [GitHub unauthenticated limit] → one API call per analysis, 10-minute cache of digests per repository.

## Migration Plan

Deploy backend and UI together. No schema change; old documents read with empty `sources` and `MANUAL` origin. Rollback: previous images read the new fields leniently.
