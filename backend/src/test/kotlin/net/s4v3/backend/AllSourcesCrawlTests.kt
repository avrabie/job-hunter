package net.s4v3.backend

import net.s4v3.backend.sources.AtsCrawl
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.BoardResult
import net.s4v3.backend.sources.CrawlResult
import net.s4v3.backend.sources.internal.AllSourcesCrawl
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class AllSourcesCrawlTests {

    private fun crawl(vararg results: BoardResult) = object : AtsCrawl {
        override fun run() = CrawlResult(results.toList())
    }

    private fun throwing(e: Exception) = object : AtsCrawl {
        override fun run(): CrawlResult = throw e
    }

    private fun ok(board: String) = BoardResult(board, BoardOutcome.COMPLETED, created = 1)

    @Test
    fun `the results of both sources come in one list, Greenhouse first`() {
        val all = AllSourcesCrawl(listOf("greenhouse" to crawl(ok("a"), ok("b")), "swissdevjobs" to crawl(ok("swissdevjobs"))))

        assertEquals(listOf("a", "b", "swissdevjobs"), all.run().boards.map { it.board })
    }

    @Test
    fun `failing Greenhouse boards do not stop SwissDevJobs`() {
        val failed = BoardResult("a", BoardOutcome.FAILED, reason = "timeout")
        val all = AllSourcesCrawl(listOf("greenhouse" to crawl(failed), "swissdevjobs" to crawl(ok("swissdevjobs"))))

        assertEquals(listOf(failed, ok("swissdevjobs")), all.run().boards)
    }

    @Test
    fun `a throwing SwissDevJobs gives a failed result and keeps the Greenhouse results`() {
        val all = AllSourcesCrawl(
            listOf("greenhouse" to crawl(ok("a")), "swissdevjobs" to throwing(IllegalStateException("secret text"))),
        )

        val boards = all.run().boards

        assertEquals(ok("a"), boards[0])
        assertEquals(BoardResult("swissdevjobs", BoardOutcome.FAILED, reason = "unexpected IllegalStateException"), boards[1])
    }

    @Test
    fun `a throwing Greenhouse does not stop SwissDevJobs`() {
        val all = AllSourcesCrawl(
            listOf("greenhouse" to throwing(IllegalStateException()), "swissdevjobs" to crawl(ok("swissdevjobs"))),
        )

        assertEquals(listOf(BoardOutcome.FAILED, BoardOutcome.COMPLETED), all.run().boards.map { it.outcome })
    }

    @Test
    fun `a disabled adapter adds no result`() {
        val all = AllSourcesCrawl(listOf("greenhouse" to crawl(ok("a")), "swissdevjobs" to crawl()))

        assertEquals(listOf("a"), all.run().boards.map { it.board })
    }
}

@IntegrationTest
internal class AllSourcesCrawlWiringTests(@Autowired private val context: ApplicationContext) {

    @Test
    fun `the crawl that other beans get is the combined one`() {
        assertIs<AllSourcesCrawl>(context.getBean(AtsCrawl::class.java))
    }
}
