package net.s4v3.backend.applications

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Where the user stands with a tracked job. Any stage can be changed to any other at any time. */
enum class ApplicationStage { INTERESTED, APPLYING, APPLIED, REJECTED }

/** What the shared posting looks like now compared with the frozen copy. `GONE`: not seen for 14 days, or no longer exists. */
enum class LiveStatus { OPEN, CHANGED, GONE }

/** One line of the list: no description and no notes. */
data class ApplicationSummary(
    val id: UUID,
    val postingId: UUID?,
    val title: String,
    val company: String,
    val stage: ApplicationStage,
    val appliedOn: LocalDate?,
    val stageChangedAt: Instant,
    val liveStatus: LiveStatus,
)

/** One application with the frozen job text. */
data class ApplicationDetail(
    val id: UUID,
    val postingId: UUID?,
    val title: String,
    val company: String,
    val stage: ApplicationStage,
    val appliedOn: LocalDate?,
    val stageChangedAt: Instant,
    val liveStatus: LiveStatus,
    val url: String,
    val description: String,
    val language: String,
    val capturedAt: Instant,
    val postingLastSeen: Instant?,
    val notes: String,
    val letterCount: Int,
    /** True when the live posting's description differs from the frozen copy. */
    val liveChanged: Boolean,
)

/** The frozen copy of a job, for the letter writer. */
data class FrozenJob(val title: String, val company: String, val description: String, val language: String)

/** The stage of the caller's application for one posting, for the postings list. */
data class PostingTracking(val applicationId: UUID, val stage: ApplicationStage)

/** A change to an application. A null field is left as it is. */
data class ApplicationPatch(
    val stage: ApplicationStage? = null,
    val notes: String? = null,
    val appliedOn: LocalDate? = null,
)

/** The result of tracking a posting: [created] is false when the user already tracked it. */
data class TrackResult(val application: ApplicationDetail, val created: Boolean)

/** The user already has [ApplicationService.MAX_PER_USER] applications. */
class ApplicationLimitException : RuntimeException("Application limit reached")

/** A patch broke a rule; [code] is a fixed word, never input. */
class InvalidApplicationPatchException(val code: String) : RuntimeException(code)

/** The caller's tracked jobs. Every method is scoped by [sub], the token's `sub`; another user's id is the same as an unknown id. */
interface ApplicationService {

    /** Null when the posting does not exist. Idempotent. Throws [ApplicationLimitException] for the 501st. */
    fun track(sub: String, postingId: UUID): TrackResult?

    fun get(sub: String, id: UUID): ApplicationDetail?

    /** Newest stage change first. */
    fun list(sub: String, stage: ApplicationStage? = null): List<ApplicationSummary>

    /** Null when there is no such application. Throws [InvalidApplicationPatchException]. */
    fun update(sub: String, id: UUID, patch: ApplicationPatch): ApplicationDetail?

    /** True when something was removed. The letters go with it. */
    fun remove(sub: String, id: UUID): Boolean

    /** The caller's applications for the given postings (at most [MAX_LOOKUP] ids are looked at). */
    fun byPosting(sub: String, postingIds: Collection<UUID>): Map<UUID, PostingTracking>

    fun frozenJob(sub: String, id: UUID): FrozenJob?

    companion object {
        const val MAX_PER_USER = 500
        const val MAX_LOOKUP = 100
        const val MAX_NOTES = 5000
    }
}
