# CV export

How the saved profile becomes a CV file, and what the pipeline depends on. The behaviour is specified in `openspec/specs/cv-export` and
`openspec/specs/ui-cv-export` once the change `export-profile-cv` is archived.

## Pipeline

`Profile` + options -> `CvBuilder` -> `CvDocument` -> a renderer.

- `CvBuilder` (`backend/.../cv/export/internal`) decides what is printed and in which words (`Labels`: English and German headings, months,
  levels, work authorisation). It never prints preferences, salary or cantons, and never translates the user's own text.
- `MarkdownRenderer`, `HtmlRenderer` and `PdfRenderer` only lay the document out. `PdfRenderer` renders the HTML of `HtmlRenderer`, so HTML
  and PDF look the same. Templates are inline stylesheets in `Templates.kt` (CSS 2.1 only: floats and tables, no flexbox or grid, because
  the PDF renderer implements CSS 2.1).
- All profile strings go through one `esc()` function; links are emitted only for http(s); the PDF renderer refuses every external
  resource.

## Professional template and photo

`CvTemplate.PROFESSIONAL` (HTML and PDF; Markdown ignores the template) is laid out by separate code paths in `HtmlRenderer` and its own block in
`Templates.kt`, so CLASSIC, MODERN and COMPACT stay byte-identical (pinned by `CvGoldenHtmlTests` against `src/test/resources/cv-export/golden`; set
`GOLDEN_UPDATE=1` to rewrite them only when a change is meant).

- Look: navy `#1f3a68` capital name, orange `#c0792a` headline and company, contact lines under the name, letter-spaced section headings with a
  navy rule, justified summary, entries as a two-row table (title and period, then company and place), square bullets with a bold lead-in (the
  text before the first `: ` when shorter than 40 characters). Only Source Sans 3; CSS 2.1 only.
- Skills are a two-column table of groups (`SkillGroups`, labels in `Labels.skillGroup`). The flat list stays in the other templates.
- Footer: `Name · Headline` on every PDF page through `@page { @bottom-center { content: "..." } }`. openhtmltopdf supports margin boxes, so the
  `position: running()` fallback was not needed. The text is CSS-escaped (every character except ASCII letters, digits and spaces as `\hex `);
  the box needs its own `font-family`, it does not inherit from `body`.
- Photo: `photoFileId` in the export request (the caller's own JPEG, read through `FileCabinet.image`, re-encoded by `PhotoProcessor` to a
  320 x 320 round JPEG without metadata). `CvBuilder.build(profile, options, photo)` takes the processed bytes and stays pure; `DefaultExportService`
  reads and processes the photo only for PROFESSIONAL with HTML or PDF, under the same two render permits as PDF rendering. Any problem (unknown,
  someone else's, not a JPEG by content, too large, damaged) is `400 {"error":"photo_invalid"}`; with another template or Markdown the id is ignored.
  HTML embeds it as a `data:image/jpeg;base64` URI and the CSP gets `img-src data:` only when a photo is present; `PdfRenderer` resolves only that
  data URI. The photo, its name and its id are never logged.

## Dependencies and licences

| What | Version | Licence | Notes |
| --- | --- | --- | --- |
| `io.github.openhtmltopdf:openhtmltopdf-pdfbox` | 1.1.100 | LGPL 2.1 or later | HTML to PDF; used unmodified as a library; builds on PDFBox 3.0.8, the version already used to read CVs |
| Source Sans 3 (Regular, Italic, Bold, Bold Italic) | release 3.052R | SIL Open Font Licence 1.1 | bundled in `backend/src/main/resources/cv-export/fonts/` with `OFL.txt` and `README.txt` (source URL and version); embedded in every PDF so German and French text render the same everywhere |

Everything is pure Java plus bundled files, so it behaves the same on a developer machine and in the Linux amd64 image (no system fonts or
native tools are needed; the container's root filesystem stays read-only).

## Looking at output

Render a profile with the three templates and both languages in a throwaway test, then convert page 1 to an image to look at it
(on macOS `sips -s format png file.pdf --out file.png`; this is only a local inspection aid, not part of the app).

The same photo can be shown on the public page and served at `/api/public/cv/<handle>/photo`; see `docs/cv-public.md` (section Photo).
