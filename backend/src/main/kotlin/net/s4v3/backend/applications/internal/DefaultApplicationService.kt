package net.s4v3.backend.applications.internal

import net.s4v3.backend.applications.ApplicationDetail
import net.s4v3.backend.applications.ApplicationLimitException
import net.s4v3.backend.applications.ApplicationNotAppliedException
import net.s4v3.backend.applications.ApplicationPatch
import net.s4v3.backend.applications.ApplicationService
import net.s4v3.backend.applications.ApplicationStage
import net.s4v3.backend.applications.ApplicationSummary
import net.s4v3.backend.applications.FrozenJob
import net.s4v3.backend.applications.InvalidApplicationPatchException
import net.s4v3.backend.applications.JobData
import net.s4v3.backend.applications.LiveStatus
import net.s4v3.backend.applications.PostingTracking
import net.s4v3.backend.applications.RavProof
import net.s4v3.backend.applications.TrackResult
import net.s4v3.backend.postings.PostingLiveState
import net.s4v3.backend.postings.PostingQuery
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The tracking rules. Logs outcomes and counts only: never a description, a title or notes. */
@Service
internal class DefaultApplicationService(
    private val repository: ApplicationRepository,
    private val postings: PostingQuery,
    private val clock: Clock = Clock.systemUTC(),
) : ApplicationService {

    private val log = LoggerFactory.getLogger(DefaultApplicationService::class.java)

    @Transactional
    override fun track(sub: String, postingId: UUID): TrackResult? {
        repository.lockUser(sub)
        repository.findByPosting(sub, postingId)?.let { return TrackResult(detail(sub, it), created = false) }
        val posting = postings.find(postingId) ?: return null
        if (repository.count(sub) >= ApplicationService.MAX_PER_USER) {
            log.info("Application refused: limit reached")
            throw ApplicationLimitException()
        }
        val now = Instant.now(clock)
        val row = ApplicationRow(
            id = UUID.randomUUID(), postingId = postingId, stage = ApplicationStage.INTERESTED,
            title = posting.title, company = posting.company, url = posting.links.firstOrNull { it.primary }?.url ?: posting.url,
            description = posting.description, jobLanguage = posting.language, capturedAt = now,
            postingLastSeen = posting.lastSeen, contentHash = posting.contentHash, notes = "", appliedOn = null, stageChangedAt = now,
        )
        repository.insert(sub, row, now)
        log.info("Application tracked")
        return TrackResult(detail(sub, row), created = true)
    }

    @Transactional
    override fun trackImported(sub: String, job: JobData): TrackResult {
        repository.lockUser(sub)
        repository.findImported(sub, job.url)?.let { return TrackResult(detail(sub, it), created = false) }
        if (repository.count(sub) >= ApplicationService.MAX_PER_USER) {
            log.info("Application refused: limit reached")
            throw ApplicationLimitException()
        }
        val now = Instant.now(clock)
        val row = ApplicationRow(
            id = UUID.randomUUID(), postingId = null, stage = ApplicationStage.INTERESTED, title = job.title, company = job.company,
            url = job.url, description = job.description, jobLanguage = job.language, capturedAt = now, postingLastSeen = null,
            contentHash = "", notes = "", appliedOn = null, stageChangedAt = now, source = IMPORT,
        )
        repository.insert(sub, row, now)
        log.info("Imported application tracked")
        return TrackResult(detail(sub, row), created = true)
    }

    @Transactional(readOnly = true)
    override fun get(sub: String, id: UUID): ApplicationDetail? = repository.find(sub, id)?.let { detail(sub, it) }

    @Transactional(readOnly = true)
    override fun list(sub: String, stage: ApplicationStage?): List<ApplicationSummary> {
        val rows = repository.list(sub, stage)
        val live = postings.liveState(rows.mapNotNull { it.postingId })
        return rows.map { summary(it, live) }
    }

    @Transactional
    override fun update(sub: String, id: UUID, patch: ApplicationPatch): ApplicationDetail? {
        val row = repository.find(sub, id) ?: return null
        val notes = patch.notes
        if (notes != null && notes.length > ApplicationService.MAX_NOTES) throw InvalidApplicationPatchException("notes_invalid")
        val now = Instant.now(clock)
        val newStage = patch.stage ?: row.stage
        val stageChanged = newStage != row.stage
        var appliedOn = patch.appliedOn ?: row.appliedOn
        if (stageChanged && newStage == ApplicationStage.APPLIED && patch.appliedOn == null && row.appliedOn == null) {
            appliedOn = now.atZone(clock.zone).toLocalDate()
        }
        val updated = row.copy(
            stage = newStage,
            notes = notes ?: row.notes,
            appliedOn = appliedOn,
            stageChangedAt = if (stageChanged) now else row.stageChangedAt,
        )
        if (!repository.save(sub, updated, now)) return null
        log.info("Application updated: stageChanged={}", stageChanged)
        return detail(sub, updated)
    }

    @Transactional
    override fun remove(sub: String, id: UUID): Boolean =
        repository.delete(sub, id).also { if (it) log.info("Application removed") }

    @Transactional(readOnly = true)
    override fun byPosting(sub: String, postingIds: Collection<UUID>): Map<UUID, PostingTracking> =
        repository.byPosting(sub, postingIds.distinct().take(ApplicationService.MAX_LOOKUP))
            .associate { (posting, found) -> posting to PostingTracking(found.first, found.second) }

    @Transactional(readOnly = true)
    override fun frozenJob(sub: String, id: UUID): FrozenJob? =
        repository.find(sub, id)?.let { FrozenJob(it.title, it.company, it.description, it.jobLanguage) }

    /**
     * Only the title and the original link are stored for an application. The company address, the contact person, phone
     * and e-mail and the postal code are not, so they are null: nothing is guessed from the description.
     */
    @Transactional(readOnly = true)
    override fun ravProof(sub: String, id: UUID): RavProof? {
        val row = repository.find(sub, id) ?: return null
        if (row.stage != ApplicationStage.APPLIED) throw ApplicationNotAppliedException()
        return RavProof(
            companyAddress = null,
            position = row.title,
            contactPerson = null,
            contactPhone = null,
            contactEmail = null,
            postalCode = null,
            postingUrl = row.url,
        )
    }

    private fun status(row: ApplicationRow, live: Map<UUID, PostingLiveState>): LiveStatus {
        if (row.source == IMPORT) return LiveStatus.OPEN // nothing live to compare with
        val state = row.postingId?.let { live[it] } ?: return LiveStatus.GONE
        if (state.lastSeen.isBefore(Instant.now(clock).minus(GONE_AFTER))) return LiveStatus.GONE
        return if (state.contentHash != row.contentHash) LiveStatus.CHANGED else LiveStatus.OPEN
    }

    private fun summary(row: ApplicationRow, live: Map<UUID, PostingLiveState>) = ApplicationSummary(
        row.id, row.postingId, row.title, row.company, row.stage, row.appliedOn, row.stageChangedAt, status(row, live),
    )

    private fun detail(sub: String, row: ApplicationRow): ApplicationDetail {
        val live = row.postingId?.let { postings.liveState(listOf(it)) }.orEmpty()
        val status = status(row, live)
        return ApplicationDetail(
            id = row.id, postingId = row.postingId, title = row.title, company = row.company, stage = row.stage,
            appliedOn = row.appliedOn, stageChangedAt = row.stageChangedAt, liveStatus = status, url = row.url,
            description = row.description, language = row.jobLanguage, capturedAt = row.capturedAt,
            postingLastSeen = row.postingId?.let { live[it]?.lastSeen } ?: row.postingLastSeen, notes = row.notes,
            letterCount = repository.letterCount(sub, row.id),
            liveChanged = row.postingId?.let { live[it] }?.let { it.contentHash != row.contentHash } ?: false,
        )
    }

    private companion object {
        const val IMPORT = "IMPORT"
        val GONE_AFTER: Duration = Duration.ofDays(14)
    }
}
