package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvLink
import net.s4v3.backend.cv.export.CvPhoto
import net.s4v3.backend.cv.export.CvRenderer
import net.s4v3.backend.cv.export.CvSection
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.EntrySection
import net.s4v3.backend.cv.export.GroupedTagSection
import net.s4v3.backend.cv.export.LineSection
import net.s4v3.backend.cv.export.ProseSection
import net.s4v3.backend.cv.export.TagSection
import java.util.Base64

/** Lays a [CvDocument] out as one standalone XHTML file. Every string goes through [esc]; no scripts, no external resources. */
internal class HtmlRenderer : CvRenderer {

    override fun render(document: CvDocument, template: CvTemplate): ByteArray =
        html(document, template).toByteArray(Charsets.UTF_8)

    /** [extraHead] is added to `<head>` after the title; the default adds nothing, so every existing output stays as it was. */
    fun html(document: CvDocument, template: CvTemplate, extraHead: String = ""): String = buildString {
        append("<!DOCTYPE html>\n")
        append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"${document.language.name.lowercase()}\">\n<head>\n")
        append("<meta charset=\"UTF-8\"/>\n")
        val professional = template == CvTemplate.PROFESSIONAL
        val photo = if (professional) document.photo else null
        val img = if (photo != null) "; img-src data:" else ""
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'$img\"/>\n")
        append("<title>${esc(document.name)}</title>\n")
        append(extraHead)
        append("<style>${Templates.css(template, if (professional) footer(document) else null)}</style>\n</head>\n<body>\n")
        if (professional) {
            professionalHeader(document, photo)
            document.sections.forEach { professionalSection(it) }
        } else {
            header(document)
            if (template == CvTemplate.MODERN) modernBody(document) else document.sections.forEach { section(it) }
        }
        append("</body>\n</html>\n")
    }

    /** The PDF footer text: `Name · Headline` (the headline only when the document has one). */
    private fun footer(d: CvDocument): String = listOfNotNull(d.name, d.headline?.takeIf { it.isNotBlank() }).joinToString(" · ")

    /** PROFESSIONAL: name, headline and contact lines left, the round photo (already round in its pixels) right. */
    private fun StringBuilder.professionalHeader(d: CvDocument, photo: CvPhoto?) {
        append("<table class=\"top\"><tr>\n<td class=\"who\">\n<h1>${esc(d.name)}</h1>\n")
        d.headline?.let { append("<p class=\"headline\">${esc(it)}</p>\n") }
        d.contactLines.forEach { append("<p class=\"contact\">${link(it)}</p>\n") }
        d.facts.forEach { append("<p class=\"facts\">${esc(it)}</p>\n") }
        append("</td>\n")
        if (photo != null) {
            val data = Base64.getEncoder().encodeToString(photo.bytes)
            append("<td class=\"photo\"><img src=\"data:image/jpeg;base64,$data\" alt=\"\"/></td>\n")
        }
        append("</tr></table>\n")
    }

    private fun StringBuilder.professionalSection(s: CvSection) {
        append("<div class=\"section\">\n<h2>${esc(s.title)}</h2>\n")
        when (s) {
            is ProseSection -> s.paragraphs.forEach { append("<p class=\"prose\">${esc(it)}</p>\n") }
            is GroupedTagSection -> {
                append("<table class=\"skills\">\n")
                s.groups.forEach {
                    append("<tr><td class=\"group\">${esc(it.name)}</td><td>${esc(it.tags.joinToString(", "))}</td></tr>\n")
                }
                append("</table>\n")
            }
            is TagSection -> append("<p class=\"prose\">${esc(s.tags.joinToString(", "))}</p>\n")
            is LineSection -> s.lines.forEach { append("<p class=\"line\">${esc(it)}</p>\n") }
            is EntrySection -> s.entries.forEach { professionalEntry(it) }
        }
        append("</div>\n")
    }

    /** Title (navy, left) and period (navy, right); below it the organisation (orange) and the place (italic grey, right). */
    private fun StringBuilder.professionalEntry(e: CvEntry) {
        val (title, org) = if (e.titleFirst && e.subheading != null) e.subheading to e.heading else e.heading to e.subheading
        append("<div class=\"entry\">\n<table class=\"entry\">\n")
        append("<tr><td class=\"title\">${esc(title)}</td><td class=\"period\">${esc(e.period.orEmpty())}</td></tr>\n")
        if (org != null || !e.place.isNullOrBlank()) {
            append("<tr><td class=\"org\">${esc(org.orEmpty())}</td><td class=\"place\">${esc(e.place.orEmpty())}</td></tr>\n")
        }
        append("</table>\n")
        e.link?.let { append("<p class=\"link\">${link(it)}</p>\n") }
        e.text?.let { append("<p class=\"context\">${esc(it)}</p>\n") }
        if (e.bullets.isNotEmpty()) {
            append("<ul class=\"pro\">")
            e.bullets.forEach { append("<li>${leadIn(it)}</li>") }
            append("</ul>\n")
        }
        if (e.tags.isNotEmpty()) append("<p class=\"tags\">${esc(e.tags.joinToString(", "))}</p>\n")
        append("</div>\n")
    }

    /** The text before the first `: ` is bold (with the colon) when it is shorter than 40 characters. */
    private fun leadIn(text: String): String {
        val i = text.indexOf(": ")
        return if (i in 1 until LEAD_IN_MAX) "<strong>${esc(text.substring(0, i + 1))}</strong>${esc(text.substring(i + 1))}" else esc(text)
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
            is GroupedTagSection -> append("<p>${esc(s.groups.flatMap { it.tags }.joinToString(", "))}</p>\n")
            is LineSection -> s.lines.forEach { append("<p>${esc(it)}</p>\n") }
            is EntrySection -> s.entries.forEach { entry(it) }
        }
        append("</div>\n")
    }

    private fun StringBuilder.entry(e: CvEntry) {
        append("<div class=\"entry\">\n")
        append("<p class=\"head\">${esc(e.heading)}</p>\n")
        e.link?.let { append("<p class=\"link\">${link(it)}</p>\n") }
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

    private companion object {
        const val LEAD_IN_MAX = 40
    }
}
