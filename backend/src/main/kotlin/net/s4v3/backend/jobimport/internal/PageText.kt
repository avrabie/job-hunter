package net.s4v3.backend.jobimport.internal

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Plain text of a web page for the extractor. */
internal object PageText {
    const val MAX_CHARS = 30_000
    const val MIN_CHARS = 300

    /** A visible text shorter than this, next to embedded job data, is taken to be a placeholder and left out. */
    const val PLACEHOLDER_CHARS = 1_000

    /** Scripts larger than this are not read; the appended structured text is capped at [MAX_STRUCTURED_CHARS]. */
    const val MAX_SCRIPT_CHARS = 2_000_000
    const val MAX_SCRIPT_OBJECTS = 20
    const val MAX_SCRIPT_ATTEMPTS = 200
    const val MAX_STRUCTURED_CHARS = 20_000
    private const val MIN_DESCRIPTION_CHARS = 200
    private const val MAX_STATE_DESCRIPTIONS = 3
    private const val MAX_DEPTH = 40
    private const val SEPARATOR = "--- Structured data embedded in the page ---"
    private const val VISIBLE_LABEL = "--- Visible text of the page ---"

    private const val BLOCKS = "p, div, li, ul, ol, h1, h2, h3, h4, h5, h6, br, tr, section, article"
    private val spaces = Regex("[\\s\\u00a0]+")
    private val blankRuns = Regex("\n{3,}")
    private val htmlTag = Regex("<[a-zA-Z/][^>]*>")
    private val mapper = JsonMapper.builder().build()

    /**
     * The readable text capped at [MAX_CHARS], or `null` when under [MIN_CHARS] (empty or script-only page).
     * Job data found in JSON-LD or embedded page-state scripts is put before the visible text. Scripts are only parsed, never run.
     */
    fun extract(html: String): String? {
        val doc = Jsoup.parse(html)
        val structured = structuredText(doc.select("script"))
        doc.select("script, style, noscript, nav, footer, form").remove()
        val visible = toText(doc.body())
        if (structured.isEmpty()) return visible.takeIf { it.length >= MIN_CHARS }?.take(MAX_CHARS)
        // A page that renders its job with JavaScript often shows only a placeholder ("this job has been closed") as visible
        // text. That would contradict the embedded job, so a thin visible text is left out and the embedded data stands alone;
        // otherwise the embedded data comes first and the visible text follows.
        if (visible.length < PLACEHOLDER_CHARS) return "$SEPARATOR\n$structured".takeIf { it.length >= MIN_CHARS }?.take(MAX_CHARS)
        val head = "$SEPARATOR\n$structured\n\n$VISIBLE_LABEL\n"
        return head + visible.take(MAX_CHARS - head.length).trimEnd()
    }

    private fun toText(root: Element): String {
        root.select(BLOCKS).forEach { it.appendText("\n") }
        return root.wholeText()
            .lines()
            .joinToString("\n") { it.replace(spaces, " ").trim() }
            .replace(blankRuns, "\n\n")
            .trim()
    }

    private fun htmlToText(fragment: String): String {
        val body = Jsoup.parseBodyFragment(fragment).body()
        body.select("script, style, noscript, nav, footer, form").remove()
        return toText(body)
    }

    private fun structuredText(scripts: List<Element>): String {
        val blocks = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (script in scripts) {
            val data = script.data()
            if (data.isBlank() || data.length > MAX_SCRIPT_CHARS) continue
            val type = script.attr("type").lowercase()
            if (type == "application/ld+json") {
                val root = parse(data) ?: continue
                jobPostings(root, 0).forEach { p -> jobPostingBlock(p)?.let { if (seen.add(it)) blocks += it } }
            } else if (type.isEmpty() || "javascript" in type || type == "module" || type == "application/json") {
                val found = mutableListOf<String>()
                embeddedObjects(data).forEach { stateBlocks(it, found, 0) }
                found.forEach { if (seen.add(it)) blocks += it }
            }
        }
        val out = StringBuilder()
        for (block in blocks) {
            if (out.length + block.length + 2 > MAX_STRUCTURED_CHARS) {
                val room = MAX_STRUCTURED_CHARS - out.length - 2
                if (room > 200) out.append(block.take(room)).append('\n')
                break
            }
            out.append(block).append("\n\n")
        }
        return out.toString().trim()
    }

    private fun parse(text: String): JsonNode? =
        try {
            mapper.readTree(text.trim())
        } catch (_: JacksonException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    /**
     * The JSON objects assigned in an inline script (`var x = x || {...}; x.data = {...};`), or the whole body when it is JSON.
     * A script often holds several, and the job is not necessarily the first, so every top-level object is read.
     */
    private fun embeddedObjects(data: String): List<JsonNode> {
        parse(data)?.let { if (it.isObject || it.isArray) return listOf(it) }
        val out = mutableListOf<JsonNode>()
        var from = 0
        var attempts = 0
        while (out.size < MAX_SCRIPT_OBJECTS && attempts++ < MAX_SCRIPT_ATTEMPTS) {
            val start = data.indexOf('{', from)
            if (start < 0) break
            val end = objectEnd(data, start)
            if (end < 0) break
            val node = parse(data.substring(start, end + 1))
            if (node != null && node.isObject) {
                out.add(node)
                from = end + 1
            } else {
                from = start + 1
            }
        }
        return out
    }

    /** Index of the brace that closes the object opened at [start], or -1. */
    private fun objectEnd(data: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until data.length) {
            val c = data[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return -1
    }

    private fun isJobPosting(node: JsonNode): Boolean {
        val type = node.path("@type")
        return (type.isString && type.asString() == "JobPosting") ||
            (type.isArray && type.any { it.isString && it.asString() == "JobPosting" })
    }

    private fun jobPostings(node: JsonNode, depth: Int): List<JsonNode> {
        if (depth > 4) return emptyList()
        return when {
            node.isArray -> node.flatMap { jobPostings(it, depth + 1) }
            node.isObject && isJobPosting(node) -> listOf(node)
            node.isObject -> jobPostings(node.path("@graph"), depth + 1)
            else -> emptyList()
        }
    }

    private fun str(node: JsonNode, key: String): String? {
        val v = node.path(key)
        return if (v.isString) v.asString().trim().takeIf { it.isNotEmpty() } else null
    }

    private fun jobPostingBlock(p: JsonNode): String? {
        val lines = mutableListOf<String>()
        str(p, "title")?.let { lines += "Title: $it" }
        val org = p.path("hiringOrganization")
        val orgName = if (org.isObject) str(org, "name") else if (org.isString) org.asString() else null
        orgName?.let { lines += "Company: $it" }
        val places = p.path("jobLocation").let { if (it.isArray) it.toList() else listOf(it) }
        places.forEach { place ->
            val a = place.path("address")
            val parts = if (a.isObject) {
                listOf("streetAddress", "postalCode", "addressLocality", "addressRegion", "addressCountry").mapNotNull {
                    val v = a.path(it)
                    when {
                        v.isString -> v.asString().trim()
                        v.isObject -> str(v, "name")
                        else -> null
                    }?.takeIf { s -> s.isNotEmpty() }
                }
            } else if (a.isString) listOf(a.asString()) else emptyList()
            if (parts.isNotEmpty()) lines += "Location: " + parts.joinToString(", ")
        }
        str(p, "employmentType")?.let { lines += "Employment type: $it" }
        str(p, "url")?.let { lines += "Url: $it" }
        listOf("email", "telephone", "contactPoint").forEach { key ->
            val v = p.path(key)
            when {
                v.isString -> lines += "Contact: ${v.asString()}"
                v.isObject -> listOf("name", "email", "telephone").mapNotNull { str(v, it) }
                    .takeIf { it.isNotEmpty() }?.let { lines += "Contact: " + it.joinToString(", ") }
            }
        }
        str(p, "description")?.let { d ->
            val text = if (htmlTag.containsMatchIn(d)) htmlToText(d) else d
            if (text.isNotBlank()) lines += "Description:\n$text"
        }
        if (lines.none { it.startsWith("Description:") || it.startsWith("Title:") }) return null
        return "Job posting (schema.org JSON-LD)\n" + lines.joinToString("\n")
    }

    /** Objects in page state whose "description" is a long HTML string; siblings title/location/city/applyUrl ride along. */
    private fun stateBlocks(node: JsonNode, out: MutableList<String>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_STATE_DESCRIPTIONS) return
        if (node.isArray) {
            node.forEach { stateBlocks(it, out, depth + 1) }
        } else if (node.isObject) {
            val d = node.path("description")
            if (d.isString && d.asString().length >= MIN_DESCRIPTION_CHARS && htmlTag.containsMatchIn(d.asString())) {
                val lines = mutableListOf("Job details (embedded page data)")
                str(node, "title")?.let { lines += "Title: $it" }
                listOf("location", "city").forEach { k -> str(node, k)?.let { lines += "${k.replaceFirstChar(Char::uppercase)}: $it" } }
                str(node, "applyUrl")?.let { lines += "Apply url: $it" }
                lines += "Description:\n" + htmlToText(d.asString())
                out += lines.joinToString("\n")
            }
            node.properties().forEach { (_, v) -> if (v.isContainer) stateBlocks(v, out, depth + 1) }
        }
    }
}
