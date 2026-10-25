# cv

Purpose: two features on the user's data. Extract: read the master CV into a profile draft. Export: render the saved profile as a CV file (Markdown, HTML, PDF).

## Public API
- No classes in the base package. `ExportService` and its types are in the sub-package `cv/export` and used only inside this module. Whether Modulith exposes `cv.export`: unverified.

## Internal
- `internal/CvExtractController`: `POST /api/cv/extract[?section=experience|education|publications|contact]`; master CV from `files`, text with `CvText` (PDFBox, no OCR), `CvReader` from `llm`. Returns a draft, stores nothing.
- `internal/CvExportController`: `POST /api/cv/export`; strict request, `ExportLimiter`, `ExportService`.
- `internal/ExtractionLimiter`, `ExportLimiter`: in-memory rolling hour per user.
- `export/internal/DefaultExportService` (loads the saved profile by `sub`), `RenderGuard` (2 PDF renders, 5 s wait), `CvBuilder`, `Labels` (EN/DE), `MarkdownRenderer`, `HtmlRenderer`, `PdfRenderer` (openhtmltopdf, bundled fonts), `Templates`.
- `export/CvDocument.kt`, `export/ExportTypes.kt`, `export/ExportService.kt`: the document model and options.
- `CvSettings`, `ExportSettings`.

## Events
- None.

## Tables
- None.

## Settings
- `jobhunter.cv.max-text-chars`, `jobhunter.cv.extractions-per-hour`, `jobhunter.cv-export.per-hour`.

## Calls
- `accounts`, `files` (`FileCabinet`), `llm` (`CvReader`, `LlmFailure`), `profile`.

## Tests (`backend/src/test/kotlin/net/s4v3/backend/`)
- `CvExtractApiTests`, `CvExportApiTests`, `CvSchemaTests` (legacy table).
- Unit: `CvBuilderTests`, `LabelsTests`, `MarkdownRendererTests`, `cv/export/internal/HtmlRendererTests`, `PdfRendererTests`.
- More: `docs/cv-export.md`.

Read on 2026-10-24 from: cv/internal/*.kt, cv/export/*.kt, cv/export/internal/DefaultExportService.kt, test file names.
