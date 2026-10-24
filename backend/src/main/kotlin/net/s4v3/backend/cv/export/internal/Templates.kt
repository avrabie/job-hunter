package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvTemplate

/** One inline stylesheet per template. CSS 2.1 only (floats and tables, no flexbox or grid). */
internal object Templates {

    private const val BASE = """
@page { size: A4; margin: 18mm 16mm; }
body { font-family: "Source Sans 3", Arial, sans-serif; color: #000000; margin: 0; }
h1 { margin: 0; }
h2 { margin: 0; }
ul { margin: 2pt 0 0 0; padding-left: 14pt; }
p { margin: 0; }
a { color: inherit; text-decoration: none; }
.entry { page-break-inside: avoid; }
.head { font-weight: bold; }
.meta { font-style: italic; }
.tags { font-style: italic; }
td { vertical-align: top; }
table.layout { width: 100%; border-collapse: collapse; }
"""

    private const val CLASSIC = """
body { font-size: 10.5pt; line-height: 1.3; }
h1 { font-size: 22pt; }
h2 { font-size: 12pt; margin-top: 12pt; border-bottom: 1px solid #000000; text-transform: uppercase; }
.entry { margin-top: 6pt; }
.contact, .facts { margin-top: 3pt; }
"""

    private const val MODERN = """
body { font-size: 10pt; line-height: 1.3; }
h1 { font-size: 24pt; color: #1f4e79; }
.headline { font-size: 12pt; color: #1f4e79; }
h2 { font-size: 11.5pt; margin-top: 12pt; color: #1f4e79; border-bottom: 2px solid #1f4e79; }
.entry { margin-top: 6pt; }
.contact, .facts { margin-top: 3pt; }
td.side { width: 32%; padding-right: 14pt; }
td.main { width: 68%; }
.side h2 { margin-top: 10pt; }
.side p { margin-top: 2pt; }
"""

    private const val COMPACT = """
@page { margin: 12mm 13mm; }
body { font-size: 9pt; line-height: 1.2; }
h1 { font-size: 16pt; }
h2 { font-size: 10pt; margin-top: 6pt; border-bottom: 1px solid #000000; text-transform: uppercase; }
.entry { margin-top: 3pt; }
ul { margin-top: 1pt; }
.contact, .facts { margin-top: 1pt; }
"""

    private const val PROFESSIONAL = """
@page { margin: 16mm 18mm 22mm 18mm; }
body { font-size: 10pt; line-height: 1.3; color: #222222; }
h1 { font-size: 24pt; line-height: 1.1; color: #1f3a68; text-transform: uppercase; }
.headline { font-size: 13pt; color: #c0792a; margin-top: 3pt; }
.contact { font-size: 9.5pt; margin-top: 1pt; }
.facts { font-size: 9.5pt; }
table.top { width: 100%; border-collapse: collapse; }
td.who { vertical-align: top; }
td.photo { width: 36mm; text-align: right; vertical-align: top; }
td.photo img { width: 34mm; height: 34mm; }
h2 { font-size: 11pt; margin-top: 14pt; margin-bottom: 5pt; color: #1f3a68; text-transform: uppercase;
  letter-spacing: 3pt; border-bottom: 1.5pt solid #1f3a68; padding-bottom: 2pt; }
p.prose { text-align: justify; margin-top: 3pt; }
table.entry { width: 100%; border-collapse: collapse; margin-top: 8pt; page-break-inside: avoid; }
table.entry td { padding: 0; }
td.title { font-weight: bold; color: #1f3a68; }
td.period { font-weight: bold; color: #1f3a68; text-align: right; white-space: nowrap; padding-left: 10pt; }
td.org { font-weight: bold; color: #c0792a; }
td.place { font-style: italic; color: #666666; text-align: right; padding-left: 10pt; }
.context { font-style: italic; margin-top: 2pt; }
.entry-body { page-break-inside: auto; }
ul.pro { list-style-type: square; margin: 3pt 0 0 0; padding-left: 14pt; }
ul.pro li { margin-top: 1.5pt; text-align: left; }
.tags { color: #666666; margin-top: 3pt; }
table.skills { width: 100%; border-collapse: collapse; }
table.skills td { padding: 2pt 0; }
table.skills tr { page-break-inside: avoid; }
td.group { width: 34%; font-weight: bold; color: #1f3a68; padding-right: 10pt; }
.line { margin-top: 2pt; }
"""

    fun css(template: CvTemplate, footer: String? = null): String = BASE + when (template) {
        CvTemplate.CLASSIC -> CLASSIC
        CvTemplate.MODERN -> MODERN
        CvTemplate.COMPACT -> COMPACT
        CvTemplate.PROFESSIONAL -> PROFESSIONAL + footerRule(footer)
    }

    /** A page footer box on every page of the PDF; the text is escaped as a CSS string (every other character as `\\hex `). */
    private fun footerRule(footer: String?): String {
        if (footer.isNullOrBlank()) return ""
        val escaped = buildString {
            footer.codePoints().forEach { cp ->
                if (cp < 128 && (Character.isLetterOrDigit(cp) || cp == ' '.code)) append(cp.toChar()) else append("\\").append(Integer.toHexString(cp)).append(' ')
            }
        }
        return "@page { @bottom-center { content: \"$escaped\"; font-family: \"Source Sans 3\", Arial, sans-serif; font-size: 8pt; color: #666666; } }\n"
    }
}
