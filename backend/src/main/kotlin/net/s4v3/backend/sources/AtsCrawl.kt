package net.s4v3.backend.sources

/** Starts one crawl of the configured applicant-tracking-system boards. */
interface AtsCrawl {

    /** Crawls every configured board, one after the other. Never throws for a board that fails; see [BoardResult]. */
    fun run(): CrawlResult
}

/** One entry per configured board, in configuration order. Empty when no board is configured. */
data class CrawlResult(val boards: List<BoardResult>)

enum class BoardOutcome { COMPLETED, NOT_MODIFIED, TOO_SOON, BLOCKED_BY_ROBOTS, FAILED, ALREADY_RUNNING }

/**
 * The counts are set only for [BoardOutcome.COMPLETED]. [reason] is a short fixed text for a failed board and never
 * contains response content.
 */
data class BoardResult(
    val board: String,
    val outcome: BoardOutcome,
    val created: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val skipped: Int = 0,
    val filteredOut: Int = 0,
    val reason: String? = null,
)
