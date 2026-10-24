# cx-render report (tasks 3.1 to 3.3)

## Finished (ticked)
- 3.1: dependency `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.100` added after the PDFBox line (latest/release on Maven Central).
  `./gradlew compileKotlin` OK; `dependencies --configuration runtimeClasspath | grep pdfbox` shows only pdfbox 3.0.8 (fontbox, pdfbox-io, xmpbox same version).
  Fonts: SourceSans3-Regular, -It, -Bold, -BoldIt .ttf + OFL.txt + README.txt in `backend/src/main/resources/cv-export/fonts/`.
  Source: https://github.com/adobe-fonts/source-sans/releases/download/3.052R/TTF-source-sans-3.052R.zip (release 3.052R).
  The TTF zip has no licence file; OFL.txt is LICENSE.md of tag 3.052R from the same repo (fetched via `gh api`).
- 3.2: Html.kt (`esc`, `isWebUrl`), HtmlRenderer.kt, Templates.kt. `HtmlRendererTests`: 6 tests, 0 failures.
- 3.3: PdfRenderer.kt. `PdfRendererTests`: 6 tests, 0 failures (magic, name + `ä ö ü é è ç` in all templates, all fonts embedded,
  COMPACT 1 page, 30 jobs paginate with every employer, markup characters as written, no request to a local HttpServer).
- Verify run: `./gradlew test --tests '*HtmlRenderer*' --tests '*PdfRenderer*'` BUILD SUCCESSFUL (12 tests).

## STAND-IN (lead: delete at merge)
`backend/src/main/kotlin/net/s4v3/backend/cv/export/StandInDocumentModel.kt` holds exactly the Contract's CvTemplate, CvLanguage, CvLink,
CvEntry, CvSection + 4 implementations, CvDocument, CvRenderer. It does NOT contain CvFormat, CvSectionKind, ExportOptions (group 2).

## Files
- backend/build.gradle.kts
- backend/src/main/resources/cv-export/fonts/{SourceSans3-Regular,-It,-Bold,-BoldIt}.ttf, OFL.txt, README.txt
- backend/src/main/kotlin/net/s4v3/backend/cv/export/StandInDocumentModel.kt
- backend/src/main/kotlin/net/s4v3/backend/cv/export/internal/{Html,HtmlRenderer,Templates,PdfRenderer}.kt
- backend/src/test/kotlin/net/s4v3/backend/cv/export/internal/{CvFixtures,HtmlRendererTests,PdfRendererTests}.kt
- openspec/changes/export-profile-cv/tasks.md (3.1-3.3 ticked)
- handoff/cx-render.report.md

## Samples (synthetic, 3 jobs; throwaway test removed)
Dir: /private/tmp/claude-501/-Users-moldo-Documents-code-projects-job-hunter/6cf7a101-886a-41c1-9bf0-bcabbef38647/scratchpad/cv-samples/
cv-classic-en.pdf, cv-classic-de.pdf, cv-modern-en.pdf, cv-modern-de.pdf, cv-compact-en.pdf, cv-compact-de.pdf
I did not view them; layout is for the lead to judge.

## Notes and decisions
- Design choices: MODERN puts TagSection and LineSection (skills, languages) in the narrow left column, other sections on the right.
  So section order in MODERN differs visually; content is identical (tested).
- Links: only http(s) become `<a>`; `<a>` in PDF are annotations, never fetched.
- External resources: `useUriResolver { null }` (nothing resolves) plus a refusing `HttpStreamFactory`. I did not subclass NaiveUserAgent;
  PdfRendererBuilder builds its own user agent. Because profile text is escaped, the `<img>` in the network test is text, so that test
  proves no fetch through the whole pipeline but not the resolver in isolation.
- Fonts are embedded as subsets; CSS family list falls back to Arial but the Source Sans faces cover all tested text.
- `HtmlRenderer.html()` returns String (used by PdfRenderer); `render()` returns UTF-8 bytes.
- Test fixtures use the stand-in types, so CvFixtures must be adapted if group 2 differs from the Contract.
- Not run: full `./gradlew build` / ModularityTests (not asked for group 3).
