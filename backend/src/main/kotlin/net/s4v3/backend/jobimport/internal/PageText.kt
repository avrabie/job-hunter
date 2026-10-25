package net.s4v3.backend.jobimport.internal

import org.jsoup.Jsoup

/** Plain text of a web page for the extractor. */
internal object PageText {
    const val MAX_CHARS = 30_000
    const val MIN_CHARS = 300

    private const val BLOCKS = "p, div, li, ul, ol, h1, h2, h3, h4, h5, h6, br, tr, section, article"
    private val spaces = Regex("[\\s\\u00a0]+")
    private val blankRuns = Regex("\n{3,}")

    /** The readable text capped at [MAX_CHARS], or `null` when under [MIN_CHARS] (empty or script-only page). */
    fun extract(html: String): String? {
        val doc = Jsoup.parse(html)
        doc.select("script, style, noscript, nav, footer, form").remove()
        doc.select(BLOCKS).forEach { it.appendText("\n") }
        val text = doc.body().wholeText()
            .lines()
            .joinToString("\n") { it.replace(spaces, " ").trim() }
            .replace(blankRuns, "\n\n")
            .trim()
        if (text.length < MIN_CHARS) return null
        return text.take(MAX_CHARS)
    }
}
