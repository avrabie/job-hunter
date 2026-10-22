package net.s4v3.backend.postings

import java.time.Instant
import java.util.UUID

/**
 * Published by `postings` once, when a new canonical posting is created (not for updates, repeats, or a posting linked
 * to an existing one). Carries identifiers only, no posting text. No consumers yet; `matching` is expected to subscribe.
 */
data class JobIngested(val postingId: UUID, val ingestedAt: Instant)
