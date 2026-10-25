package net.s4v3.backend.web.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.postings.Posting
import net.s4v3.backend.postings.PostingLink
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.postings.PostingSearch
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

internal data class LinkView(val sourceId: String, val url: String, val primary: Boolean)

internal data class PostingSummary(
    val id: UUID,
    val title: String,
    val company: String,
    val city: String?,
    val canton: String?,
    val language: String,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val links: List<LinkView>,
    val userAdded: Boolean,
)

internal data class PostingDetail(
    val id: UUID,
    val title: String,
    val company: String,
    val city: String?,
    val canton: String?,
    val language: String,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val description: String,
    val links: List<LinkView>,
    val userAdded: Boolean,
)

/**
 * The shared postings, readable by any signed-in user. Neither answer holds the raw payload (the public type has none).
 * The search text is passed on and never logged.
 */
@RestController
@Tag(name = "Postings", description = "The crawled job postings and their original links")
internal class PostingsController(private val postings: PostingQuery) {

    @GetMapping("/api/postings")
    @Operation(
        summary = "List and search the crawled postings",
        description = "Newest first-seen first, one page at a time (size at most 100). `q` matches title or company, " +
            "ignoring case; `canton` is a two-letter code; `source` is a source id. Each posting carries one link per source.",
        responses = [
            ApiResponse(responseCode = "200", description = "A page of postings"),
            ApiResponse(responseCode = "400", description = "page is negative, or canton or source has an invalid form"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) canton: String?,
        @RequestParam(required = false) source: String?,
    ): ResponseEntity<Any> {
        if (page < 0) return invalid("page")
        if (canton != null && canton.isNotEmpty() && !CANTON.matches(canton)) return invalid("canton")
        if (source != null && source.isNotEmpty() && !SOURCE.matches(source)) return invalid("source")
        val found = postings.search(
            PostingSearch(
                text = q?.take(MAX_TEXT), canton = canton?.uppercase()?.takeIf { it.isNotEmpty() },
                sourceId = source?.takeIf { it.isNotEmpty() }, page = page, size = size.coerceIn(1, PostingQuery.MAX_PAGE_SIZE),
            ),
        )
        return ResponseEntity.ok(found.map(::summary))
    }

    @GetMapping("/api/postings/{id}")
    @Operation(
        summary = "Read one posting with its description and original links",
        responses = [
            ApiResponse(responseCode = "200", description = "The posting"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
            ApiResponse(responseCode = "404", description = "No posting with this id"),
        ],
    )
    fun detail(@PathVariable id: String): ResponseEntity<Any> {
        val posting = runCatching { UUID.fromString(id) }.getOrNull()?.let(postings::find)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail(posting))
    }

    private fun invalid(field: String): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(mapOf("error" to "invalid_parameter", "field" to field))

    private fun links(links: List<PostingLink>) = links.map { LinkView(it.sourceId, it.url, it.primary) }

    private fun summary(p: Posting) = PostingSummary(
        p.id, p.title, p.company, p.city, p.canton, p.language, p.firstSeen, p.lastSeen, links(p.links), p.userAdded,
    )

    private fun detail(p: Posting) = PostingDetail(
        p.id, p.title, p.company, p.city, p.canton, p.language, p.firstSeen, p.lastSeen, p.description, links(p.links),
        p.userAdded,
    )

    private companion object {
        val CANTON = Regex("[A-Za-z]{2}")
        val SOURCE = Regex("[A-Za-z0-9_.-]{1,100}")
        const val MAX_TEXT = 200
    }
}
