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
