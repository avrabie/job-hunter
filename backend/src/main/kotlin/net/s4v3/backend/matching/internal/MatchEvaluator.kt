package net.s4v3.backend.matching.internal

import net.s4v3.backend.postings.Posting
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.profile.ProfileDirectory
import net.s4v3.backend.profile.ProfileService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Runs the prefilter for stored profiles and postings and stores the results. Each result is written on its own, so a
 * long run never holds one transaction open. Logs hold counts and ids only: no profile content, no scores per user.
 */
@Service
internal class MatchEvaluator(
    private val profiles: ProfileService,
    private val directory: ProfileDirectory,
    private val postings: PostingQuery,
    private val store: MatchStore,
    private val settings: MatchingSettings,
) {

    private val log = LoggerFactory.getLogger(MatchEvaluator::class.java)

    /** Evaluates one posting for every user with a stored profile. A user whose evaluation fails does not stop the rest. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun evaluatePosting(postingId: UUID) {
        val posting = postings.find(postingId) ?: return
        var users = 0
        var passed = 0
        var failed = 0
        var after: String? = null
        do {
            val page = directory.page(after, ProfileDirectory.MAX_PAGE_SIZE)
            for (owned in page) {
                users++
                try {
                    val result = Prefilter.evaluate(owned.stored.profile, posting, settings.threshold)
                    store.upsert(owned.sub, posting.id, result, owned.stored.version)
                    if (result.passed) passed++
                } catch (e: Exception) {
                    failed++
                }
            }
            after = page.lastOrNull()?.sub
        } while (page.size == ProfileDirectory.MAX_PAGE_SIZE)
        log.info("Evaluated posting {} for {} users: {} passed, {} failed", postingId, users, passed, failed)
        if (users > 0 && failed == users) error("Evaluating posting $postingId failed for every user")
    }

    /** Re-evaluates every posting for one user against the stored profile; stops early if a newer version was saved. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun evaluateProfile(sub: String) {
        val stored = profiles.get(sub)
        if (stored.version == 0L) return
        var evaluated = 0
        var passed = 0
        var page = 0
        while (true) {
            val batch = postings.list(page, PostingQuery.MAX_PAGE_SIZE)
            if (batch.isEmpty()) break
            if (profiles.get(sub).version != stored.version) {
                log.info("Profile re-evaluation stopped: a newer version was saved")
                return
            }
            batch.forEach { posting: Posting ->
                val result = Prefilter.evaluate(stored.profile, posting, settings.threshold)
                store.upsert(sub, posting.id, result, stored.version)
                evaluated++
                if (result.passed) passed++
            }
            if (batch.size < PostingQuery.MAX_PAGE_SIZE) break
            page++
        }
        log.info("Re-evaluated {} postings for one user: {} passed", evaluated, passed)
    }
}
