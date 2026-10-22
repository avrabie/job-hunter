package net.s4v3.backend.postings

import java.time.Instant
import java.util.UUID

/** Read access to canonical postings. Postings are shared, so nothing here depends on who asks. */
interface PostingQuery {

    fun find(id: UUID): Posting?

    /** Newest first-seen first. [size] is capped at [MAX_PAGE_SIZE]; [page] starts at 0. */
    fun list(page: Int, size: Int): List<Posting>

    /** Postings matching [search], newest first-seen first. Wildcard characters in the text are literal. */
    fun search(search: PostingSearch): List<Posting>

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

/**
 * What to look for: [text] is a case-insensitive part of the title or company, [canton] and [sourceId] must match
 * exactly; a null field does not filter. [size] is capped at [PostingQuery.MAX_PAGE_SIZE].
 */
data class PostingSearch(
    val text: String? = null,
    val canton: String? = null,
    val sourceId: String? = null,
    val page: Int = 0,
    val size: Int = 20,
)

/** The original ad of one source. The primary source's link is first in [Posting.links]. */
data class PostingLink(val sourceId: String, val url: String, val primary: Boolean)

/**
 * A canonical posting without its raw payload. [language] is `en`, `de`, `fr`, `it` or `other`; [outOfScope] postings are
 * kept but not drafted; [needsReview] means the language could not be detected reliably.
 */
data class Posting(
    val id: UUID,
    val title: String,
    val company: String,
    val description: String,
    val url: String,
    val city: String?,
    val canton: String?,
    val workloadMin: Int?,
    val workloadMax: Int?,
    val remoteMode: RemoteMode?,
    val salaryMin: Int?,
    val salaryMax: Int?,
    val language: String,
    val outOfScope: Boolean,
    val needsReview: Boolean,
    val firstSeen: Instant,
    val lastSeen: Instant,
    /** One link per source that delivered the ad, the primary source first. */
    val links: List<PostingLink> = emptyList(),
)
