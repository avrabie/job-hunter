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

/**
 * Markdown with one structure for every template: `#` name, the headline, a bullet list of contact lines and facts,
 * `##` sections, `###` entries, bullets and comma-separated tags. Profile text is escaped, so it cannot create
 * headings, links, emphasis, HTML or lists. Only http and https addresses become links.
 */
internal class MarkdownRenderer : CvRenderer {

    override fun render(document: CvDocument, template: CvTemplate): ByteArray {
        // The template changes the layout of the other formats only; Markdown is the same for all three.
        val blocks = buildList {
            add("# ${escapeMarkdown(document.name)}")
            document.headline?.let { add(escapeMarkdown(it)) }
            val header = document.contactLines.map { "- ${link(it)}" } + document.facts.map { "- ${escapeMarkdown(it)}" }
            if (header.isNotEmpty()) add(header.joinToString("\n"))
            document.sections.forEach { addAll(section(it)) }
        }
        return (blocks.joinToString("\n\n") + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun section(section: CvSection): List<String> = buildList {
        add("## ${escapeMarkdown(section.title)}")
        when (section) {
            is ProseSection -> section.paragraphs.forEach { add(escapeMarkdown(it)) }
            is EntrySection -> section.entries.forEach { addAll(entry(it)) }
            is TagSection -> add(section.tags.joinToString(", ") { escapeMarkdown(it) })
            is LineSection -> add(bullets(section.lines))
        }
    }

    private fun entry(entry: CvEntry): List<String> = buildList {
        add("### ${escapeMarkdown(entry.heading)}")
        val meta = listOfNotNull(entry.subheading, entry.period, entry.place)
        if (meta.isNotEmpty()) add(escapeMarkdown(meta.joinToString(" · ")))
        entry.link?.let { add(link(it)) }
        entry.text?.let { add(escapeMarkdown(it)) }
        if (entry.bullets.isNotEmpty()) add(bullets(entry.bullets))
        if (entry.tags.isNotEmpty()) add(entry.tags.joinToString(", ") { escapeMarkdown(it) })
    }

    private fun bullets(items: List<String>): String = items.joinToString("\n") { "- ${escapeMarkdown(it)}" }

    private fun link(link: CvLink): String {
        val url = safeUrl(link.url) ?: return escapeMarkdown(link.text)
        return "[${escapeMarkdown(link.text)}](${destination(url)})"
    }

    /** http and https only, with no whitespace or control characters. */
    private fun safeUrl(url: String?): String? =
        url?.takeIf {
            (it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)) &&
                it.none { c -> c.isWhitespace() || c.isISOControl() }
        }

    /** Percent-encodes the characters that would end a link destination early. */
    private fun destination(url: String): String =
        url.replace("\\", "%5C").replace("(", "%28").replace(")", "%29").replace("<", "%3C").replace(">", "%3E")
}

/** Characters that are escaped anywhere in a line. */
private const val INLINE_SPECIALS = "\\`*_[]<>|~"

/** Characters that start a heading, list or rule at the start of a line. */
private const val LINE_START_SPECIALS = "#-+="

/** An ordered-list marker at the start of a line, such as `1.` or `2)`. */
private val ORDERED_MARKER = Regex("^(\\d+)([.)])")

/**
 * Escapes profile text for Markdown: inline markup characters everywhere in a line, and the markers that start a
 * heading, list, rule or ordered list at the start of a line. Text stays readable; only its structure is neutralised.
 */
internal fun escapeMarkdown(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n').split('\n').joinToString("\n") { escapeLine(it) }

private fun escapeLine(line: String): String {
    val escaped = buildString {
        for (c in line.trimStart()) {
            if (c in INLINE_SPECIALS) append('\\')
            append(c)
        }
    }
    val first = escaped.firstOrNull()
    val marked = if (first != null && first in LINE_START_SPECIALS) "\\$escaped" else escaped
    return ORDERED_MARKER.replace(marked) { "${it.groupValues[1]}\\${it.groupValues[2]}" }
}
