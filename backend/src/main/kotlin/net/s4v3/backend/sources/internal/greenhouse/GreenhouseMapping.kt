package net.s4v3.backend.sources.internal.greenhouse

import net.s4v3.backend.postings.PostingCandidate
import org.springframework.web.util.HtmlUtils
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** A job of the light list: just enough to filter on. */
internal data class ListedJob(val id: String, val title: String, val location: String?)

/** Parsing of Greenhouse board responses and mapping of a job to a [PostingCandidate]. */
internal object GreenhouseMapping {

    private val json = JsonMapper.builder().build()

    /** Throws [MalformedResponse] unless [body] is a JSON object. Jobs without an id or title are dropped. */
    fun parseList(body: String): List<ListedJob> {
        val root = parse(body)
        val jobs = root.path("jobs")
        if (!jobs.isArray) throw MalformedResponse()
        return jobs.mapNotNull { node ->
            val id = node.path("id").asString()
            val title = node.path("title").asString()
            if (id.isBlank() || title.isBlank()) null else ListedJob(id, title, node.path("location").path("name").asString())
        }
    }

    fun parseJob(body: String): JsonNode {
        val root = parse(body)
        if (!root.isObject) throw MalformedResponse()
        return root
    }

    /** Null when the job lacks an id, title, company or URL, or has no description text. */
    fun toCandidate(board: String, job: JsonNode): PostingCandidate? {
        val id = job.path("id").asString()
        val title = job.path("title").asString().trim()
        val company = job.path("company_name").asString().trim()
        val url = job.path("absolute_url").asString().trim()
        if (id.isBlank() || title.isEmpty() || company.isEmpty() || url.isEmpty()) return null
        val description = DescriptionText.fromContent(job.path("content").asString())
        if (description.isBlank()) return null
        val place = SwissLocation.find(job.path("location").path("name").asString())
        return PostingCandidate(
            sourceId = "greenhouse-$board",
            sourcePostingId = id,
            url = url,
            title = title,
            company = company,
            description = description,
            rawPayload = job.toString(),
            city = place?.city,
            canton = place?.canton,
        )
    }

    private fun parse(body: String): JsonNode =
        try {
            json.readTree(body)
        } catch (e: tools.jackson.core.JacksonException) {
            throw MalformedResponse()
        }.also { if (!it.isObject) throw MalformedResponse() }
}

/** Never carries the response text, which could end up in logs. */
internal class MalformedResponse : RuntimeException("The response is not the expected JSON")

/** Plain text from the HTML that the API delivers escaped inside its JSON `content` field. */
internal object DescriptionText {

    private val scriptLike = Regex("<(script|style)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val itemOpen = Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val lineBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val blockEdge = Regex("</?(?:p|div|h[1-6]|ul|ol|table|section|tr)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("<[^>]*>")

    fun fromContent(content: String): String {
        if (content.isBlank()) return ""
        // The API escapes the HTML once for JSON transport, so it is unescaped first, then stripped, then unescaped again.
        var text = HtmlUtils.htmlUnescape(content)
        text = scriptLike.replace(text, "")
        text = itemOpen.replace(text, "\n\u0001")
        text = lineBreak.replace(text, "\n")
        text = blockEdge.replace(text, "\n\n")
        text = anyTag.replace(text, "")
        text = HtmlUtils.htmlUnescape(text).replace(' ', ' ')
        text = text.replace(Regex("\u0001\\s*"), "- ")
        return text.lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
