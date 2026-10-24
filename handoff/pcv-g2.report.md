# pcv-g2 report: task 2.1 (builder contact visibility)

## Tasks finished
- 2.1 ticked in `openspec/changes/public-cv-page/tasks.md`.

## Verify output
- `./gradlew test --tests "CvBuilderTests" --tests "MarkdownRendererTests" --tests "HtmlRendererTests" --tests "PdfRendererTests"`: BUILD SUCCESSFUL.
  - CvBuilderTests 20 tests (16 existing, 4 new), 0 failures.
  - MarkdownRendererTests 5, HtmlRendererTests 6, PdfRendererTests 6; 0 failures.
- `./gradlew integrationTest --tests "CvExportApiTests"`: BUILD SUCCESSFUL, 14 tests, 0 failures.
- Full suite not run (as instructed). Nothing committed.

## What changed
- `cv/export/ExportTypes.kt`: `data class ContactVisibility(email, phone, website, linkedin, codeAccounts, location, headline = true)`
  with `ALL` and `NONE` in the companion; `ExportOptions.contact: ContactVisibility = ContactVisibility.ALL` as the last parameter.
- `cv/export/internal/CvBuilder.kt`: headline is null when `headline` is off; each contact line is gated by its flag
  (email, phone, location, website, LinkedIn, code accounts). Name and sections are unchanged. Work authorisation
  (`facts`) still follows `includeWorkAuthorisation` only.
- `backend/src/test/kotlin/net/s4v3/backend/CvBuilderTests.kt`: the `options()` helper takes `contact`; new tests:
  - default (no option) equals `ContactVisibility.ALL` (whole CvDocument equal);
  - each of the six contact items dropped on its own, the other five stay; headline off drops the headline;
  - email and phone off: absent from Markdown, HTML (all templates) and PDF text (PDFBox), website present in Markdown;
  - all off, every language: header is only the name (work authorisation off too), no headline, Markdown first block is `# Ada Example`.

## Notes for the lead
- Scenario "All off" in the spec says the header shows only the name. Work authorisation is a separate option
  (`includeWorkAuthorisation`, as the task says), so the all-off test sets it to false. With it on, the fact line still prints.
- Experience `place` (the job's location) is not a contact item and is unchanged; only the personal `location` is gated.
- Not touched: `profile/`, `web/`, other `cv/` packages, UI, docs.
