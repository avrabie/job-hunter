# cx-endpoint report (task 4.1)

## Finished
- 4.1 ticked. `cd backend && ./gradlew build`: BUILD SUCCESSFUL. 573 tests, 0 failures, 0 skipped.
  `CvExportApiTests`: 14 tests, 0 failures. `ModularityTests`: 2 tests, 0 failures.
- (No gradlew at the repo root; it is in `backend/`.)

## Scenario coverage in CvExportApiTests
PDF export with headers; Markdown and HTML same order; profile unchanged; other user; anonymous 401;
bad request (DOCX, unknown field, bad section, null section/boolean, missing field, not JSON, empty);
`profile` field 400; no name 409; never saved 409; sections select/order and private data (salary, role,
canton) absent in MD/HTML/PDF; analysis on/off, work authorisation on/off, open source off; hourly limit 429
(per-hour=6 in the test) and per-user; busy 503 (test holds both RenderGuard permits, HTML still works);
nothing in logs (name, body marker, summary, email absent; outcome line present).

## Design
- Public entry point in `cv/export`: `ExportService` (+ `ExportedCv`, `NameRequiredException`,
  `ExportBusyException`, `ExportFailedException`). Impl `DefaultExportService` and `RenderGuard`
  (Semaphore(2), 5 s wait, PDF only) are internal in `cv/export/internal`.
- Controller (`cv/internal/CvExportController`) parses the body bytes with a strict Jackson mapper
  (unknown fields, nulls for booleans, trailing tokens rejected); limiter runs after parsing, before rendering.
- Order: 400, 401 (security), 429, then 409/503/500. A 409 still consumes one allowance.
- One log line: format, template, bytes, ms. Exceptions drop their causes so no library message reaches a log.
- File name slug: accents stripped, ss for sharp s, ASCII, fallback `cv`.

## Files
Created: backend/src/main/kotlin/net/s4v3/backend/cv/export/ExportService.kt,
cv/export/internal/DefaultExportService.kt, cv/internal/CvExportController.kt, ExportLimiter.kt, ExportSettings.kt,
backend/src/test/kotlin/net/s4v3/backend/CvExportApiTests.kt.
Changed: backend/src/main/resources/application.yaml (`jobhunter.cv-export.per-hour: 30`),
openspec/changes/export-profile-cv/tasks.md (tick 4.1).

## Notes
- The busy test takes about 5 s (the real wait).
- Not committed.
