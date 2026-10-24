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

    fun css(template: CvTemplate): String = BASE + when (template) {
        CvTemplate.CLASSIC -> CLASSIC
        CvTemplate.MODERN -> MODERN
        CvTemplate.COMPACT -> COMPACT
        CvTemplate.PROFESSIONAL -> CLASSIC
    }
}
