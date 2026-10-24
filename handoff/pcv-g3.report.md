# public-cv-page, group 3 (tasks 3.1, 3.2), backend

## Result
- 3.1 and 3.2 done and ticked. Verify: fast tier `WindowLimiterTests` 7, `RenderCacheTests` 6, `PreviewTicketsTests` 5, `HtmlRendererHeadTests` 2, `ModularityTests` 2: all pass. Integration `PublicCvServiceTests` 35 and `CvExportApiTests` 14: all pass. `compileKotlin compileTestKotlin --rerun-tasks`: no `w:`/`e:`.
- Added scenario "Remove while locked" to `specs/cv-public/spec.md`.

## Public API (`net.s4v3.backend.cv.publiccv`)
- `PublicCvService`: `state(sub): PublicCvState?` (null = not configured), `save(...)`: `SaveResult`, `remove(sub): RemoveResult` (`REMOVED, NOT_FOUND, LOCKED`; the controller maps REMOVED and NOT_FOUND to 204, LOCKED to 409 `locked`), `renderPublic(handle): RenderedPage?`, `preview(sub, settings): PreviewTicket`, `renderPreview(PreviewTicket): RenderedPage?`, `takeDown(handle)`, `unlock(handle)` (Boolean).
- `SaveResult`: `Saved(state)`, `ValidationFailed(fields)` (field `handle`, fixed message), `Reserved`, `Taken`, `Held`, `ChangeTooSoon(at)`, `NameRequired`, `Locked`. `PreviewTicket(value: String)` is my definition (the Contract did not give one).
- Internal for group 4: `PublicCvLimits` (beans `pageByIp, notFoundByIp, pageByHandle, handleChecks, previews`, each a `WindowLimiter` with `tryAcquire(key)` and `retryAfter(key): Duration` for `Retry-After`), settings `jobhunter.public-cv.*` (`PublicCvLimitSettings`, defaults 60/10/600/60/30 documented in `application.yaml`), `PreviewTickets`, `RenderCache` bean (500 entries, 10 min).

## Decisions as instructed
- Order in save: handle form/reserved, locked+publish, name (publish only), taken, held (own hold allowed and removed), change-too-soon (only when the handle differs from the stored one). First claim is not a change. A change writes `HANDLE_CHANGED` and holds the old handle 90 days. Unique-index race on upsert answers `Taken`.
- A locked page may still save with `published=false` (settings/handle), the lock stays (repository never writes it).
- Events per save: `HANDLE_CHANGED`, `PUBLISHED` (became published), `PAUSED`, `SETTINGS_CHANGED` (settings differ, or a first draft). First save that publishes writes only `PUBLISHED`. Flags are `headline, analysis, workAuthorisation, location, contact.*, indexable, section.<KIND>` as booleans, never values. Takedown/unlock audit with empty flags.
- `renderPublic` normalises and validates the handle first (no database call for malformed input) and returns null for every non-served state.
- Preview: settings are forced `indexable=false`, not cached, rendered at redeem; null if the owner has no name.

## Things you did not expect
- Repository: added `lastHandleChange(sub): Instant?` (audit max of `HANDLE_CHANGED`). It gives `ChangeTooSoon.at` and `handleChangeableAt`, and survives remove and reclaim. `handle_changed_at` is still written but not used by the rules.
- `HtmlRenderer.html(document, template, extraHead = "")`: the optional parameter; default output byte-identical (tested for all templates). Head extras land right after `<title>`: robots meta and `<meta name="referrer" content="no-referrer"/>`.
- Link `rel="noopener noreferrer nofollow"` is added by the service with one string replace of `<a href="` (every anchor comes from `HtmlRenderer.link`, whose content is escaped, so nothing else can match). Export output is unchanged. A renderer option would be cleaner if you prefer.
- The HTML keeps the renderer's own CSP meta (`default-src 'none'; style-src 'unsafe-inline'`); the full CSP is a header (group 4).
- `PublicCvServiceTests` builds the service by hand with a `TestClock` (from `FilesApiTests.kt`); the integration contexts have no shared `Clock` bean, components default to `Clock.systemUTC()`.
- The sample profile's experience location prints "Zurich" when EXPERIENCE is ticked (builder behaviour, not a leak of `location`).

## Files
- Main: `cv/publiccv/PublicCvService.kt`; `cv/publiccv/internal/{DefaultPublicCvService,WindowLimiter,RenderCache,PreviewTickets,PublicCvLimits}.kt`; edited `PublicCvRepository.kt` (+`lastHandleChange`), `cv/export/internal/HtmlRenderer.kt`, `application.yaml`.
- Tests: `PublicCvServiceTests.kt`, `HtmlRendererHeadTests.kt`, `cv/publiccv/internal/{WindowLimiterTests,RenderCacheTests,PreviewTicketsTests}.kt`.
- Spec and tasks: `openspec/changes/public-cv-page/specs/cv-public/spec.md`, `tasks.md` (3.1, 3.2 ticked). Not committed.
