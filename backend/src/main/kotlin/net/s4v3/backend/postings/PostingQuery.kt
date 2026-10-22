package net.s4v3.backend.postings

import java.time.Instant
import java.util.UUID

/** Read access to canonical postings. Postings are shared, so nothing here depends on who asks. */
interface PostingQuery {

    fun find(id: UUID): Posting?

    /** Newest first-seen first. [size] is capped at [MAX_PAGE_SIZE]; [page] starts at 0. */
    fun list(page: Int, size: Int): List<Posting>

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

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
)
