package net.s4v3.backend.sources.internal

import net.s4v3.backend.sources.AtsCrawl
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.BoardResult
import net.s4v3.backend.sources.CrawlResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

/**
 * The crawl the scheduler and the startup run use: runs every source adapter in order (Greenhouse, then SwissDevJobs)
 * and returns all their results in one list. A source that throws becomes a failed result and the others still run.
 */
@Service
@Primary
internal class AllSourcesCrawl(private val sources: List<Pair<String, AtsCrawl>>) : AtsCrawl {

    @Autowired
    constructor(greenhouse: GreenhouseCrawl, swissDevJobs: SwissDevJobsCrawl) :
        this(listOf("greenhouse" to greenhouse, "swissdevjobs" to swissDevJobs))

    override fun run(): CrawlResult =
        CrawlResult(
            sources.flatMap { (name, crawl) ->
                try {
                    crawl.run().boards
                } catch (e: Exception) {
                    listOf(BoardResult(name, BoardOutcome.FAILED, reason = "unexpected ${e.javaClass.simpleName}"))
                }
            },
        )
}
