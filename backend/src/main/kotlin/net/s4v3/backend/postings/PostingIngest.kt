package net.s4v3.backend.postings

import java.util.UUID

/** Entry point for source adapters: hands one fetched posting to the shared store. */
interface PostingIngest {

    /**
     * Stores [candidate], or refreshes it if this (source, source posting id) was seen before. Idempotent and safe to
     * call concurrently for the same posting.
     *
     * @throws PostingRejectedException if a required field is blank; nothing is stored then
     */
    fun ingest(candidate: PostingCandidate): IngestResult
}

enum class RemoteMode { ONSITE, HYBRID, REMOTE }

/**
 * A posting as a source adapter delivers it, already mapped to our fields. [workloadText] is free text such as
 * "80-100%"; [salaryMin] and [salaryMax] are yearly CHF amounts and stay null when the ad gives none.
 * [rawPayload] is the source's own text, stored unchanged.
 */
data class PostingCandidate(
    val sourceId: String,
    val sourcePostingId: String,
    val url: String,
    val title: String,
    val company: String,
    val description: String,
    val rawPayload: String,
    val city: String? = null,
    val canton: String? = null,
    val workloadText: String? = null,
    val remoteMode: RemoteMode? = null,
    val salaryMin: Int? = null,
    val salaryMax: Int? = null,
)

enum class IngestOutcome { CREATED, UPDATED, UNCHANGED }

/**
 * [CREATED] means this source reference is new: either a new canonical posting, or an existing one found on another
 * source (then [postingId] is that existing posting and no `JobIngested` event was published).
 */
data class IngestResult(val postingId: UUID, val outcome: IngestOutcome)

/** [field] names the blank field; the value is never part of the message. */
class PostingRejectedException(val field: String) : RuntimeException("The posting has an empty $field")
