package net.s4v3.backend.matching.internal

import net.s4v3.backend.postings.JobIngested
import net.s4v3.backend.profile.ProfileSaved
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** Both listeners are idempotent: evaluating again replaces the stored result, so redelivery changes nothing. */
@Component
internal class MatchListeners(private val evaluator: MatchEvaluator) {

    @ApplicationModuleListener
    fun on(event: JobIngested) = evaluator.evaluatePosting(event.postingId)

    @ApplicationModuleListener
    fun on(event: ProfileSaved) = evaluator.evaluateProfile(event.userId)
}
