# Design

## Context

`Profile` is a language-neutral JSON document: `location`, `workAuthorisation`, `languages`, `summary`, `experience`, `education`, `publications`, `codeAccounts`, `openSourceProjects` (with optional `analysis`), `skills` (with `origin`, `sources`), `preferences`. It has no name or contact data. The `cv` module (master CV upload, `/api/cv/extract`) depends on `llm` and `files`; `profile` imports nothing from `cv`, so `cv` may call the public `ProfileService`. PDFBox 3.0.8 is already a dependency (it reads uploaded CVs).

## Goals / Non-Goals

**Goals:** one tested pipeline profile -> document model -> three renderers; three templates; EN and DE; no new module; nothing invented.
**Non-Goals:** see proposal.

## Decisions

1. **Deterministic rendering, no Claude.** The facts are in the profile; the work is layout and wording of labels. Alternative: let Claude write prose per template — rejected: cost, invention risk, nothing to validate.
2. **One document model, three renderers.** `CvBuilder` turns `Profile` + options into a `CvDocument` whose strings are already localised and formatted (labels, months, levels). `MarkdownRenderer`, `HtmlRenderer` and `PdfRenderer` only lay it out, so wording is fixed in one place and the renderers stay simple. PDF is the HTML renderer's output fed to openhtmltopdf, so HTML and PDF look the same by construction.
3. **PDF library.** `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.100` (Maven Central, current release; its POM depends on `org.apache.pdfbox:pdfbox:3.0.8`, the version already used). Alternatives: hand layout with PDFBox (no new dependency, but text wrapping, pagination and three templates by hand), a client-side PDF library (not testable on the server, bigger bundle), print-to-PDF in the browser (no file the user can rely on). Licence: LGPL 2.1+, used unmodified as a library, to be noted in `docs`. It renders CSS 2.1 only: templates use floats and tables, not flexbox or grid.
4. **Fonts.** The PDF needs embedded fonts for umlauts and accents. Bundle Source Sans 3 (Regular, Italic, Bold, Bold Italic, static TTF, SIL OFL 1.1) under `backend/src/main/resources/cv-export/fonts/` with its licence file, and register them with the renderer; the HTML uses `font-family: "Source Sans 3", Arial, sans-serif`. The agent downloads the files from the official release and records the source URL and version in the licence note. If a glyph is missing the renderer falls back to the font's notdef; a test prints German and French sample text and extracts it back.
5. **Templates are CSS plus a small structure choice.** `CLASSIC` (single column, serif headings off, no colour, ATS-safe), `MODERN` (two-column table: narrow left column for contact, skills, languages; accent colour from a fixed palette), `COMPACT` (single column, smaller type and tighter spacing, aims at one A4 page for a typical profile). One stylesheet per template, inline in the HTML (standalone file), `@page` A4 margins for PDF and print. Markdown ignores the template except for heading style (`#`/`##`, one list style) and is the same for all three (documented).
6. **Request and response.** `POST /api/cv/export` with JSON options; the server reads the caller's saved profile (never the request) so the CV always matches the master profile. Response bytes with `Content-Type` (`text/markdown; charset=utf-8`, `text/html; charset=utf-8`, `application/pdf`) and `Content-Disposition: attachment; filename="<slug>-cv-<lang>.<ext>"`, `Cache-Control: no-store`. The UI uses the same endpoint with format `HTML` for the preview.
7. **Contact block.** `Profile.contact: Contact` (`fullName`, `headline`, `email`, `phone`, `website`, `linkedin`, all optional, `Contact()` default). Validation: text limits, a simple email shape (`x@y.z`, no spaces), phone characters (`+`, digits, spaces, `-`, `(`, `)`, `/`, at most 30), URLs `https` only (the existing helper). `fullName` is required **for export** (409 `name_required`), not for saving, so an old profile still saves.
8. **Escaping and external resources.** Every profile string goes through one `esc()` (`& < > " '`) before entering HTML; no raw HTML from the profile is ever emitted; links are emitted only when they are `http(s)` and are never fetched. The renderer is configured with a user agent callback that refuses every external URI (no images, no stylesheets, no fonts by URL), so a link or a crafted text cannot trigger a request. The standalone HTML sets a CSP meta (`default-src 'none'; style-src 'unsafe-inline'`) and the UI preview sits in `<iframe sandbox srcdoc>` with no permissions.
9. **What is printed.** Header: name, headline, contact lines (email, phone, location, website, LinkedIn, code account links), optional work authorisation line. Sections in this default order, each omitted when empty: Profile (summary), Experience, Open source projects, Education, Skills, Languages, Publications. Experience: employer, title, period, location, achievements as bullets, technologies as tags. Open source: name (link), language, stars, description, the user's note, technologies from the analysis (when `includeAnalysis`) and its summary. Skills: name, with the level in words when set; skills from analysis have no level. Languages: language name and level in words. Preferences are never printed.
10. **Localisation.** `CvLanguage { EN, DE }`. Labels in a map per language. Months: EN `Mar 2021`, DE `03.2021`; current: `Present` / `heute`; periods `Mar 2021 – Present`. Language names for the ISO codes come from `java.util.Locale.forLanguageTag(code).getDisplayLanguage(locale)`. Levels in words (`Native` / `Muttersprache`, `Advanced` / `Fortgeschritten`, ...). German text uses Swiss spelling (no ß) in labels. The user's own text is never translated.
11. **Limits.** Per user 30 exports per rolling hour (`jobhunter.cv-export.per-hour`, in-memory, copy of the analysis limiter), PDF at most 2 renders at a time (a semaphore; a third waits up to 5 s then 503 `busy`), output over 5 MB refused (cannot happen within profile limits, guarded anyway).
12. **UI.** New route `/export` ("Export CV"), linked from the profile page and the shell. Left: options (format, template, language, section checkboxes, include open source, include analysis summaries, include work authorisation). Right: preview (HTML, sandboxed iframe, refreshed on change with a short debounce) and a Download button (fetch with the token, blob, download via an object URL). The API call is one function in `exportApi.ts`.

## Contract (do not change)

Request: `POST /api/cv/export` body
`{"format":"PDF","template":"MODERN","language":"DE","sections":["PROFILE","EXPERIENCE","OPEN_SOURCE","EDUCATION","SKILLS","LANGUAGES","PUBLICATIONS"],"includeOpenSource":true,"includeAnalysis":false,"includeWorkAuthorisation":true}`.
`format`: `MARKDOWN | HTML | PDF`. `template`: `CLASSIC | MODERN | COMPACT`. `language`: `EN | DE`. `sections` optional (default: all, in the order above); the given list selects and orders. Booleans optional (defaults `true`, `false`, `true`).
Errors, fixed bodies `{"error":"<code>"}`: 400 `invalid_request`, 409 `name_required`, 429 `too_many_exports`, 503 `busy`, 500 `render_failed`.
Contact JSON: `"contact":{"fullName":"Ada Example","headline":"Senior Kotlin Engineer","email":"ada@example.org","phone":"+41 79 000 00 00","website":"https://example.org","linkedin":"https://www.linkedin.com/in/ada-example"}`; `Profile.contact` is always present (all-null by default).
Kotlin (package `net.s4v3.backend.cv.export`): `enum CvFormat { MARKDOWN, HTML, PDF }`, `enum CvTemplate { CLASSIC, MODERN, COMPACT }`, `enum CvLanguage { EN, DE }`, `enum CvSectionKind { PROFILE, EXPERIENCE, OPEN_SOURCE, EDUCATION, SKILLS, LANGUAGES, PUBLICATIONS }`, `data class ExportOptions(format, template, language, sections: List<CvSectionKind>?, includeOpenSource = true, includeAnalysis = false, includeWorkAuthorisation = true)`, and the document model:
`data class CvDocument(language: CvLanguage, name: String, headline: String?, contactLines: List<CvLink>, facts: List<String>, sections: List<CvSection>)`, `data class CvLink(text: String, url: String? = null)`, `sealed interface CvSection { val title: String }` with `ProseSection(title, paragraphs: List<String>)`, `EntrySection(title, entries: List<CvEntry>)`, `TagSection(title, tags: List<String>)`, `LineSection(title, lines: List<String>)`, and `data class CvEntry(heading: String, subheading: String? = null, period: String? = null, place: String? = null, link: CvLink? = null, text: String? = null, bullets: List<String> = emptyList(), tags: List<String> = emptyList())`. Renderers: `interface CvRenderer { fun render(document: CvDocument, template: CvTemplate): ByteArray }`.

## Risks / Trade-offs

- [CSS 2.1 limits the look] → floats/tables; templates are tested by rendering the fixture and extracting text.
- [LGPL dependency] → used unmodified as a library; note in docs.
- [Font file size in the image] → four static TTFs, about 1 MB; acceptable.
- [Profile text in HTML] → single escape function, tests with `<script>`, `"><img src=x>` and `&`.
- [User prints private data by accident] → preferences and salary are never printed; work authorisation is an option.
- [A very long profile overflows a page] → PDF paginates; Compact only aims at one page.

## Migration Plan

Deploy backend and UI together. No schema change. Old profiles read with an empty `contact` and must get a name before the first export (the UI says so). Rollback: previous images ignore `contact` on read.
