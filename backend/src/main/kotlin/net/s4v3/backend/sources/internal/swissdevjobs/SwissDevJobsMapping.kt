package net.s4v3.backend.sources.internal.swissdevjobs

import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.RemoteMode
import net.s4v3.backend.sources.internal.greenhouse.DescriptionText
import net.s4v3.backend.sources.internal.greenhouse.MalformedResponse
import net.s4v3.backend.sources.internal.greenhouse.SwissLocation
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Element

/** The id of the SwissDevJobs source in `posting_source` and `source_fetch_state`. */
internal const val SWISSDEVJOBS_SOURCE_ID = "swissdevjobs"

/** Parsing of the SwissDevJobs list and feed and mapping of a job to a [PostingCandidate]. */
internal object SwissDevJobsMapping {

    private const val JOB_PAGE = "https://swissdevjobs.ch/jobs/"

    private val json = JsonMapper.builder().build()

    /**
     * The entries of the JSON list, as the board delivered them. Throws [MalformedResponse] unless [body] is a JSON
     * array. Entries that are not objects are returned as they are; [toCandidate] rejects them.
     */
    fun parseList(body: String): List<JsonNode> {
        val root = try {
            json.readTree(body)
        } catch (e: tools.jackson.core.JacksonException) {
            throw MalformedResponse()
        }
        if (!root.isArray) throw MalformedResponse()
        return root.toList()
    }

    /**
     * The description HTML of each feed item by the slug of its link. Throws [MalformedResponse] unless [body] is an
     * XML document. The document is untrusted: document type definitions and external entities are refused.
     */
    fun parseFeed(body: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = try {
            factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
                .parse(InputSource(StringReader(body)))
        } catch (e: SAXException) {
            throw MalformedResponse()
        } catch (e: ParserConfigurationException) {
            throw MalformedResponse()
        } catch (e: java.io.IOException) {
            throw MalformedResponse()
        }
        val items = document.getElementsByTagName("item")
        val descriptions = mutableMapOf<String, String>()
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val slug = slugOf(childText(item, "link") ?: continue) ?: continue
            descriptions.putIfAbsent(slug, childText(item, "description").orEmpty())
        }
        return descriptions
    }

    /** The path segment after `/jobs/` of a job link, without any query; null when the link has none. */
    fun slugOf(link: String): String? {
        val path = link.trim().substringBefore('?').substringBefore('#')
        if ("/jobs/" !in path) return null
        return path.substringAfter("/jobs/").substringBefore('/').takeIf { it.isNotBlank() }
    }

    /** The slug of a list entry, or null when it has none. */
    fun slugOf(entry: JsonNode): String? = entry.path("jobUrl").asString().trim().takeIf { it.isNotEmpty() }

    /**
     * Null when [entry] has no slug, title or company, or [descriptionHtml] has no text. The apply link of the
     * employer is not used; the posting links to the job's SwissDevJobs page.
     */
    fun toCandidate(entry: JsonNode, descriptionHtml: String): PostingCandidate? {
        if (!entry.isObject) return null
        val slug = slugOf(entry) ?: return null
        val title = entry.path("name").asString().trim()
        val company = entry.path("company").asString().trim()
        if (title.isEmpty() || company.isEmpty()) return null
        val description = DescriptionText.fromHtml(descriptionHtml)
        if (description.isBlank()) return null
        val city = entry.path("actualCity").asString().trim().ifEmpty { entry.path("cityCategory").asString().trim() }
            .takeIf { it.isNotEmpty() }
        val (salaryMin, salaryMax) = salary(entry)
        return PostingCandidate(
            sourceId = SWISSDEVJOBS_SOURCE_ID,
            sourcePostingId = slug,
            url = JOB_PAGE + slug,
            title = title,
            company = company,
            description = description,
            rawPayload = json.createObjectNode().apply {
                set("list", entry)
                put("feedDescription", descriptionHtml)
            }.toString(),
            city = city,
            canton = city?.let { SwissLocation.find(it)?.canton },
            remoteMode = remoteMode(entry.path("workplace").asString()),
            salaryMin = salaryMin,
            salaryMax = salaryMax,
        )
    }

    private fun remoteMode(workplace: String): RemoteMode? =
        when (workplace.trim().lowercase()) {
            "remote" -> RemoteMode.REMOTE
            "hybrid" -> RemoteMode.HYBRID
            "office", "onsite", "on-site" -> RemoteMode.ONSITE
            else -> null
        }

    /** Yearly CHF; both amounts or neither. */
    private fun salary(entry: JsonNode): Pair<Int?, Int?> {
        val from = amount(entry.path("annualSalaryFrom"))
        val to = amount(entry.path("annualSalaryTo"))
        return if (from != null && to != null && from <= to) from to to else null to null
    }

    private fun amount(node: JsonNode): Int? =
        if (node.isNumber && node.asLong() in 1..Int.MAX_VALUE) node.asInt() else null

    private fun childText(item: Element, name: String): String? {
        val children = item.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == name) return child.textContent
        }
        return null
    }
}
