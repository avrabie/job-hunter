package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvLink
import net.s4v3.backend.cv.export.CvRenderer
import net.s4v3.backend.cv.export.CvSection
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.EntrySection
import net.s4v3.backend.cv.export.LineSection
import net.s4v3.backend.cv.export.ProseSection
import net.s4v3.backend.cv.export.TagSection

/** Lays a [CvDocument] out as one standalone XHTML file. Every string goes through [esc]; no scripts, no external resources. */
internal class HtmlRenderer : CvRenderer {

    override fun render(document: CvDocument, template: CvTemplate): ByteArray =
        html(document, template).toByteArray(Charsets.UTF_8)

    fun html(document: CvDocument, template: CvTemplate): String = buildString {
        append("<!DOCTYPE html>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"${document.language.name.lowercase()}\">\n<head>\n")
        append("<meta charset=\"UTF-8\"/>\n")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\"/>\n")
        append("<title>${esc(document.name)}</title>\n")
        append("<style>${Templates.css(template)}</style>\n</head>\n<body>\n")
        header(document)
        if (template == CvTemplate.MODERN) modernBody(document) else document.sections.forEach { section(it) }
        append("</body>\n</html>\n")
    }

    private fun StringBuilder.header(d: CvDocument) {
        append("<div class=\"header\">\n<h1>${esc(d.name)}</h1>\n")
        d.headline?.let { append("<p class=\"headline\">${esc(it)}</p>\n") }
        if (d.contactLines.isNotEmpty()) {
            append("<p class=\"contact\">")
            append(d.contactLines.joinToString(" | ") { link(it) })
            append("</p>\n")
        }
        d.facts.forEach { append("<p class=\"facts\">${esc(it)}</p>\n") }
        append("</div>\n")
    }

    /** MODERN: skills and languages (tag and line sections) in the narrow left column, the rest on the right. */
    private fun StringBuilder.modernBody(d: CvDocument) {
        val side = d.sections.filter { it is TagSection || it is LineSection }
        val main = d.sections.filter { it !in side }
        append("<table class=\"layout\"><tr>\n<td class=\"side\">\n")
        side.forEach { section(it) }
        append("</td>\n<td class=\"main\">\n")
        main.forEach { section(it) }
        append("</td>\n</tr></table>\n")
    }

    private fun StringBuilder.section(s: CvSection) {
        append("<div class=\"section\">\n<h2>${esc(s.title)}</h2>\n")
        when (s) {
            is ProseSection -> s.paragraphs.forEach { append("<p>${esc(it)}</p>\n") }
            is TagSection -> append("<p>${esc(s.tags.joinToString(", "))}</p>\n")
            is LineSection -> s.lines.forEach { append("<p>${esc(it)}</p>\n") }
            is EntrySection -> s.entries.forEach { entry(it) }
        }
        append("</div>\n")
    }

    private fun StringBuilder.entry(e: CvEntry) {
        append("<div class=\"entry\">\n")
        val heading = e.link?.let { link(it) } ?: esc(e.heading)
        append("<p class=\"head\">$heading</p>\n")
        e.subheading?.let { append("<p>${esc(it)}</p>\n") }
        val meta = listOfNotNull(e.period, e.place).filter { it.isNotBlank() }
        if (meta.isNotEmpty()) append("<p class=\"meta\">${esc(meta.joinToString(" | "))}</p>\n")
        e.text?.let { append("<p>${esc(it)}</p>\n") }
        if (e.bullets.isNotEmpty()) {
            append("<ul>")
            e.bullets.forEach { append("<li>${esc(it)}</li>") }
            append("</ul>\n")
        }
        if (e.tags.isNotEmpty()) append("<p class=\"tags\">${esc(e.tags.joinToString(", "))}</p>\n")
        append("</div>\n")
    }

    private fun link(l: CvLink): String =
        if (isWebUrl(l.url)) "<a href=\"${esc(l.url!!.trim())}\">${esc(l.text)}</a>" else esc(l.text)
}
