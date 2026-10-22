package net.s4v3.backend

import net.s4v3.backend.FakeGreenhouse.Job
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.BoardResult
import net.s4v3.backend.sources.internal.FetchStateStore
import net.s4v3.backend.sources.internal.GreenhouseCrawl
import net.s4v3.backend.sources.internal.PoliteHttp
import net.s4v3.backend.sources.internal.SourcesSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class GreenhouseCrawlTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val state: FetchStateStore,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val dataSource: DataSource,
) {

    private val server = FakeGreenhouse()
    private val company = "Crawlco " + UUID.randomUUID()

    @AfterEach
    fun stop() = server.close()

    private fun token() = "t" + UUID.randomUUID().toString().replace("-", "").take(12)

    private fun crawl(vararg boards: String, minInterval: Duration = Duration.ZERO): GreenhouseCrawl {
        val settings = SourcesSettings(
            pause = Duration.ZERO, minInterval = minInterval, readTimeout = Duration.ofSeconds(3),
            greenhouse = SourcesSettings.Greenhouse(baseUrl = server.baseUrl, boards = boards.toList()),
        )
        return GreenhouseCrawl(settings, PoliteHttp(settings), state, ingest, dataSource)
    }

    private fun postings(board: String) =
        jdbc.queryForObject("select count(*) from posting_source where source_id = ?", Int::class.java, "greenhouse-$board")!!

    private fun job(id: Int, title: String = "Backend Engineer", location: String = "Zurich", content: String = "&lt;p&gt;Work $id&lt;/p&gt;") =
        Job(id, title, location, content, company)

    private fun fiveJobs() = listOf(
        job(1, "Backend Engineer", "Zurich"),
        job(2, "Site Reliability Engineer", "Geneva; Paris"),
        job(3, "Platform Engineer", "Remote - Switzerland"),
        job(4, "Backend Engineer", "Warsaw"),
        job(5, "Head of Growth", "Zurich"),
    )

    @Test
    fun `first run creates the Swiss IT jobs and counts the rest as filtered out`() {
        val board = token().also { server.boards[it] = fiveJobs() }

        val r = crawl(board).run().boards.single()

        assertEquals(BoardResult(board, BoardOutcome.COMPLETED, created = 3, filteredOut = 2), r)
        assertEquals(3, postings(board))
        assertFalse(server.requests.any { it.endsWith("/jobs/4") || it.endsWith("/jobs/5") }, server.requests.toString())
    }

    @Test
    fun `second run with the same content changes nothing`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        crawl(board).run()
        server.ignoreEtag = true

        val r = crawl(board).run().boards.single()

        assertEquals(BoardResult(board, BoardOutcome.COMPLETED, unchanged = 3, filteredOut = 2), r)
        assertEquals(3, postings(board))
    }

    @Test
    fun `a changed job is updated`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        crawl(board).run()
        server.boards[board] = fiveJobs().map { if (it.id == 1) it.copy(content = "&lt;p&gt;New text&lt;/p&gt;") else it }

        val r = crawl(board).run().boards.single()

        assertEquals(1, r.updated)
        assertEquals(2, r.unchanged)
        assertEquals(0, r.created)
    }

    @Test
    fun `a failing detail is skipped and the others are ingested`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        server.detailStatus[2] = 500

        val r = crawl(board).run().boards.single()

        assertEquals(BoardOutcome.COMPLETED, r.outcome)
        assertEquals(2, r.created)
        assertEquals(1, r.skipped)
        assertNull(state.etag("greenhouse-$board"), "a board with a failed detail must be retried in full")
    }

    @Test
    fun `an unknown board fails and the next board is still crawled`() {
        val good = token().also { server.boards[it] = fiveJobs() }
        val unknown = token()

        val result = crawl(unknown, good).run()

        assertEquals(listOf(BoardOutcome.FAILED, BoardOutcome.COMPLETED), result.boards.map { it.outcome })
        assertEquals(3, postings(good))
    }

    @Test
    fun `a board that robots txt disallows is not requested`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        server.robotsBody = "User-agent: *\nDisallow: /v1/boards/$board/\n"

        val r = crawl(board).run().boards.single()

        assertEquals(BoardOutcome.BLOCKED_BY_ROBOTS, r.outcome)
        assertEquals(0, server.listRequests(board))
    }

    @Test
    fun `an unreadable robots txt fails the board without fetching content`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        server.robotsStatus = 500

        val r = crawl(board).run().boards.single()

        assertEquals(BoardOutcome.FAILED, r.outcome)
        assertEquals(listOf("GET /robots.txt"), server.requests.toList())
    }

    @Test
    fun `a missing robots txt allows fetching`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        server.robotsStatus = 404

        assertEquals(BoardOutcome.COMPLETED, crawl(board).run().boards.single().outcome)
    }

    @Test
    fun `a 304 means not modified and no detail is requested`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        crawl(board).run()
        assertNotNull(state.etag("greenhouse-$board"))
        server.requests.clear()

        val r = crawl(board).run().boards.single()

        assertEquals(BoardOutcome.NOT_MODIFIED, r.outcome)
        assertFalse(server.requests.any { "/jobs/" in it }, server.requests.toString())
    }

    @Test
    fun `a failed list fetch keeps the stored validator`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        crawl(board).run()
        val stored = state.etag("greenhouse-$board")
        server.listStatus = 500

        assertEquals(BoardOutcome.FAILED, crawl(board).run().boards.single().outcome)

        assertEquals(stored, state.etag("greenhouse-$board"))
    }

    @Test
    fun `a board crawled again too soon is not requested`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        crawl(board, minInterval = Duration.ofHours(1)).run()
        server.requests.clear()

        val r = crawl(board, minInterval = Duration.ofHours(1)).run().boards.single()

        assertEquals(BoardOutcome.TOO_SOON, r.outcome)
        assertTrue(server.requests.isEmpty(), server.requests.toString())
    }

    @Test
    fun `no configured board means no request`() {
        val result = crawl().run()

        assertTrue(result.boards.isEmpty())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `an html body fails and nothing is ingested`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        server.listBody = "<html>maintenance</html>"
        server.listContentType = "text/html"

        val r = crawl(board).run().boards.single()

        assertEquals(BoardOutcome.FAILED, r.outcome)
        assertEquals(0, postings(board))
    }

    @Test
    fun `an invalid board token is refused without a request`() {
        val r = crawl("../x").run().boards.single()

        assertEquals(BoardOutcome.FAILED, r.outcome)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `two crawls at once crawl the board once and the other reports it running`() {
        val board = token().also { server.boards[it] = fiveJobs() }
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.onList = { inside.countDown(); release.await(10, TimeUnit.SECONDS) }
        val first = arrayOfNulls<BoardResult>(1)
        val runner = thread { first[0] = crawl(board).run().boards.single() }
        assertTrue(inside.await(10, TimeUnit.SECONDS))

        val second = crawl(board).run().boards.single()
        release.countDown()
        runner.join(10_000)

        assertEquals(BoardOutcome.ALREADY_RUNNING, second.outcome)
        assertEquals(BoardOutcome.COMPLETED, first[0]?.outcome)
        assertEquals(1, server.listRequests(board))
    }
}
