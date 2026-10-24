# Glossary (backend)

Domain terms as the code uses them. Read on 2026-10-24. `K/` = `backend/src/main/kotlin/net/s4v3/backend/`. See `docs/architecture.md` for how the parts fit.

## People and accounts

- **sub**: the token's subject, the only user id in the system. Every per-user row is keyed by it. `K/accounts/CurrentUser.kt`.
- **user record**: a row in `app_user` created the first time a `sub` is seen. `K/accounts/internal/UserProvisioner.kt`.
- **role**: a name in the token claim `roles` (for example `ADMIN`), mapped to `ROLE_*`. `K/accounts/internal/SecurityConfig.kt`.

## Profile

- **profile** (master profile): the facts about one person (contact, experience, education, skills, preferences, ...), stored as one JSON document per user. Types in `K/profile/Profile.kt`, storage in `K/profile/internal/JdbcProfileService.kt`.
- **stored profile / version**: `StoredProfile(version, profile)`. Version 0 means nothing is stored; the version is the optimistic lock checked on save. `K/profile/Profile.kt`.
- **profile update**: what a client sends: `ProfileUpdate(version, profile)`. `K/profile/Profile.kt`.
- **preferences**: target roles, workload range, remote modes, cantons and minimum salary; used by the prefilter, never printed on a CV. `K/profile/Profile.kt`, `K/matching/internal/Prefilter.kt`.
- **work authorisation**: Swiss permit status (`SWISS_CITIZEN`, `PERMIT_C`, ...). `K/profile/Profile.kt`.
- **publication (profile)**: an academic or written publication entry in the profile (title, venue, year, authors, link). `K/profile/Profile.kt` (`Publication`).
- **skill**: a named skill with an optional level and an origin (`MANUAL` or `ANALYSIS`); an analysis-origin skill lists the repositories that support it. `K/profile/Profile.kt`.
- **code account**: a linked code-hosting account (`CodeAccount(provider, username)`); only `GITHUB` exists. `K/profile/Profile.kt`.
- **code provider**: the hosting service enum `CodeProvider`; one provider class per value (`GitHubRepositoryProvider`, `GitHubDigestProvider`). `K/profile/internal/`.
- **repository**: a public repository as the provider describes it (`Repository`), listed by `RepositoryCatalog`. `K/profile/RepositoryCatalog.kt`.
- **open source project**: a repository the user picked and saved in the profile. `K/profile/Profile.kt` (`OpenSourceProject`).
- **snapshot**: the repository facts copied into an `OpenSourceProject` when the user picked it; later changes at the provider do not update it. `K/profile/Profile.kt`.
- **digest**: what an analysis reads from one repository: README text, file tree paths, a few build files (`RepositoryDigest`); built by `RepositoryDigestSource`. `K/profile/RepositoryDigest.kt`, `K/profile/internal/GitHubDigestProvider.kt`.
- **analysis**: the model's checked description of a project (`ProjectAnalysis`: summary, architecture, tech stack, files read). Returned to the client, which saves it into the profile; the analyse endpoint does not save. `K/profile/Profile.kt`, `K/web/internal/ProjectAnalysisController.kt`.
- **technology / evidence**: one tech-stack item of an analysis with the file path and note that cite it; one whose path is not in the digest is dropped. `K/llm/internal/AnalysisMapper.kt`.

## Files and CV

- **file cabinet**: the user's files: metadata in `user_file`, bytes on the volume. `K/files/`.
- **file kind**: `CV` (PDF, Markdown or text, up to 5 MB) or `OTHER` (anything, up to 10 GiB). `K/files/internal/JdbcFileRepository.kt`, `FilesSettings.kt`.
- **master CV**: the one file of kind `CV` the user chose; the input of CV extraction. `FileCabinet.masterCv`, `K/files/FileCabinet.kt`.
- **download ticket**: a single-use, 60 second token that lets a browser download one file without a bearer header. `K/files/internal/DownloadTickets.kt`.
- **storage key**: the generated path of a file's bytes under the volume (`<first two hex>/<uuid>`); never built from the file name. `K/files/internal/FileStorage.kt`.
- **orphan sweep**: daily job that deletes stored files no row refers to and older than a day. `K/files/internal/OrphanSweep.kt`.
- **legacy CV**: the earlier single-CV feature (`cv_document` table), moved into the cabinet at startup. `K/files/internal/LegacyCvMover.kt`.
- **draft** (profile draft): the `Profile` that extraction builds from the master CV (via `CvProfileDraft` and `DraftMapper`). It is returned, never stored; the user reviews it and saves it. `K/cv/internal/CvExtractController.kt`, `K/llm/internal/DraftMapper.kt`.
- **section extraction**: extraction limited to experience, education or publications (`ProfileSection`). `K/llm/CvReader.kt`.
- **CV document**: the layout-neutral CV (`CvDocument`) built from the profile; renderers turn it into Markdown, HTML or PDF. `K/cv/export/CvDocument.kt`.
- **template**: `CLASSIC`, `MODERN` or `COMPACT` layout; **language**: `EN` or `DE` labels. `K/cv/export/ExportTypes.kt`.
- **render guard**: the limit of two parallel PDF renders. `K/cv/export/internal/DefaultExportService.kt`.

## Language model

- **Koog**: the JetBrains LLM library; imported only by `llm`. `K/llm/internal/`, `docs/koog-notes.md`.
- **CvReader / ProjectAnalyst**: the two public services of `llm`. `K/llm/CvReader.kt`, `K/llm/ProjectAnalyst.kt`.
- **LlmFailure**: the only error `llm` reports (`NOT_CONFIGURED` or `FAILED`), with fixed text. `K/llm/CvReader.kt`.
- **repair**: resending a malformed model answer to the cheaper `Haiku_4_5` model to fix its JSON; `jobhunter.llm.repairs` tries. `K/llm/internal/KoogCvReader.kt`.
- **executor source**: the seam that creates the model client, replaced by a fake in tests. `ExecutorSource`, `K/llm/internal/KoogCvReader.kt`.

## Postings and sources

- **source**: where postings come from; its id is a string such as `greenhouse-<board>`. `K/sources/internal/GreenhouseCrawl.kt`, `posting_source.source_id`.
- **board**: one Greenhouse company board token (`jobhunter.sources.greenhouse.boards`). `K/sources/internal/SourcesSettings.kt`.
- **crawl**: one run over all configured boards (`AtsCrawl.run`), result per board as `BoardResult` with a `BoardOutcome`. `K/sources/AtsCrawl.kt`.
- **crawl schedule**: the stored on/off, cron and zone (single row, set by an admin); the scheduler polls it. `K/sources/internal/ScheduleStore.kt`, `CrawlScheduler.kt`.
- **fetch state / etag**: per board, the validator of the last accepted list and the start of the last run. `K/sources/internal/FetchStateStore.kt`.
- **polite HTTP**: GET with a fixed User-Agent, timeouts, size cap, no redirects and a per-host pause. `K/sources/internal/PoliteHttp.kt`.
- **posting**: a canonical, normalised job ad shared by all users. `K/postings/PostingQuery.kt` (`Posting`), table `posting`.
- **candidate**: a posting as a source adapter delivers it (`PostingCandidate`) before storage. `K/postings/PostingIngest.kt`.
- **ingest**: store or refresh a candidate; outcomes `CREATED`, `UPDATED`, `UNCHANGED`. `K/postings/internal/JdbcPostings.kt`.
- **primary source**: the source that created the canonical posting; only it may change the posting's fields. `posting_source.is_primary`.
- **dedupe key**: hash of normalised company, title and city that links the same ad across sources; null when the city is unknown. `K/postings/internal/Normalization.kt`.
- **content hash**: hash of the normalised content, used to detect a change. `K/postings/internal/Normalization.kt`.
- **raw payload**: the source's own text, stored unchanged (not exposed by the API). table `posting`.
- **out of scope**: a posting whose language is not `en` or `de`; stored but not drafted. **needs review**: language detection was not confident. `K/postings/internal/LanguageDetector.kt`.
- **Swiss location / role filter**: the checks that keep only Swiss IT jobs from a board. `K/sources/internal/greenhouse/Filters.kt`.

## Matching

- **match**: the stored result for one (user, posting): passed flag, score, reason codes, profile version. `K/matching/internal/MatchStore.kt`, table `posting_match`.
- **prefilter**: the deterministic first stage: hard rules, then a 0 to 100 score. No model is involved. `K/matching/internal/Prefilter.kt`.
- **reason code**: a fixed upper-case code explaining a result, for example `CANTON_MISMATCH`, `SKILL_MATCH_3`. Never text from a profile or posting. `K/matching/internal/Prefilter.kt`.
- **threshold**: `jobhunter.matching.threshold`, the lowest passing score. `K/matching/internal/MatchingSettings.kt`.

## Events and platform

- **publication** (event publication): a row in `event_publication` that records an event for a listener until the listener completes. `backend/src/main/resources/db/changelog/001-baseline.sql`.
- **module listener**: `@ApplicationModuleListener`, how `matching` receives events. `K/matching/internal/MatchListeners.kt`.
- **JobIngested / ProfileSaved / UserRegistered**: the three events, identifiers only. `K/postings/JobIngested.kt`, `K/profile/ProfileSaved.kt`, `K/accounts/UserRegistered.kt`.
- **module**: a top-level package under `net.s4v3.backend`; its base package is its public API, `internal` is private. `docs/architecture.md` section 2.
- **limiter**: an in-memory rolling-hour counter per user (`ExtractionLimiter`, `ExportLimiter`, `AnalysisLimiter`). `K/cv/internal/`, `K/web/internal/AnalysisLimiter.kt`.
- **personal data rule**: profile, CV, repository and posting text and tokens never go into logs or error bodies; code comments across the modules repeat it (for example `K/profile/ProfileDirectory.kt`).
- **applications / notifications**: planned modules with no code yet. `backend/src/main/java/net/s4v3/backend/applications/package-info.java`.
