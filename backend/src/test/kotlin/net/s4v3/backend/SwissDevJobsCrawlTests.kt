package net.s4v3.backend

import net.s4v3.backend.FakeSwissDevJobs.Job
import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.sources.BoardOutcome
import net.s4v3.backend.sources.BoardResult
import net.s4v3.backend.sources.internal.FetchStateStore
import net.s4v3.backend.sources.internal.PoliteHttp
import net.s4v3.backend.sources.internal.SourcesSettings
import net.s4v3.backend.sources.internal.SwissDevJobsCrawl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class SwissDevJobsCrawlTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val state: FetchStateStore,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val dataSource: DataSource,
) {

    private val server = FakeSwissDevJobs()
    private val company = "Swissco " + UUID.randomUUID()
    private val sourceId = "swissdevjobs"

    @BeforeEach
    fun clean() {
        // The source id is fixed, so earlier runs of this class must not leak into the counts.
        jdbc.update("delete from source_fetch_state where source_id = ?", sourceId)
        jdbc.update("delete from posting_source where source_id = ?", sourceId)
    }

    @AfterEach
    fun stop() = server.close()

    private fun crawl(enabled: Boolean = true, minInterval: Duration = Duration.ZERO): SwissDevJobsCrawl {
        val settings = SourcesSettings(
            pause = Duration.ZERO, minInterval = minInterval, readTimeout = Duration.ofSeconds(3),
            swissdevjobs = SourcesSettings.SwissDevJobs(enabled = enabled, baseUrl = server.baseUrl),
        )
        return SwissDevJobsCrawl(settings, PoliteHttp(settings), state, ingest, dataSource)
    }

    private fun postings() =
        jdbc.queryForObject("select count(*) from posting_source where source_id = ?", Int::class.java, sourceId)!!

    private fun job(slug: String, title: String = "Backend Engineer", city: String? = "Zug", inFeed: Boolean = true) =
        Job(slug, title, company, city, description = "<p>Work on $slug</p>", inFeed = inFeed)

    private fun three() = listOf(job("a-1"), job("b-2", "Platform Engineer", "Bern"), job("c-3", "Data Engineer", "Basel"))

    private fun run(c: SwissDevJobsCrawl = crawl()): BoardResult = c.run().boards.single()

    @Test
    fun `first run creates a posting for each job`() {
        server.jobs = three()

        val r = run()

        assertEquals(BoardResult("swissdevjobs", BoardOutcome.COMPLETED, created = 3), r)
        assertEquals(3, postings())
        assertNotNull(state.etag(sourceId))
    }

    @Test
    fun `the fields reach the stored posting`() {
        server.jobs = listOf(job("full-1"))

        run()

        val row = jdbc.queryForMap(
            """select p.company, p.city, p.canton, p.remote_mode, p.salary_min, p.salary_max, s.url
               from posting p join posting_source s on s.posting_id = p.id where s.source_id = ?""", sourceId,
        )
        assertEquals(company, row["company"])
        assertEquals("Zug", row["city"])
        assertEquals("ZG", row["canton"])
        assertEquals("HYBRID", row["remote_mode"].toString())
        assertEquals(80000, row["salary_min"])
        assertEquals(120000, row["salary_max"])
        assertEquals("https://swissdevjobs.ch/jobs/full-1", row["url"])
    }

    @Test
    fun `requests carry the identifying user agent and ask for a feed`() {
        server.jobs = three()

        run()

        assertTrue(server.userAgents.all { it.startsWith("JobHunter/") }, server.userAgents.toString())
        assertTrue(server.feedAccepts.single().contains("xml"), server.feedAccepts.toString())
    }

    @Test
    fun `second run with the same content changes nothing`() {
        server.jobs = three()
        run()
        server.ignoreEtag = true

        val r = run()

        assertEquals(BoardResult("swissdevjobs", BoardOutcome.COMPLETED, unchanged = 3), r)
        assertEquals(3, postings())
    }

    @Test
    fun `a 304 means not modified and the feed is not requested`() {
        server.jobs = three()
        run()
        server.requests.clear()

        val r = run()

        assertEquals(BoardOutcome.NOT_MODIFIED, r.outcome)
        assertEquals(0, server.feedRequests(), server.requests.toString())
    }

    @Test
    fun `a partner ad without a feed item is skipped, the rest is ingested and the next run asks again`() {
        server.jobs = three() + job("partner-4", inFeed = false)

        val r = run()

        assertEquals(BoardResult("swissdevjobs", BoardOutcome.COMPLETED, created = 3, skipped = 1), r)
        assertNull(state.etag(sourceId), "the list validator must stay unset while a job is outstanding")

        server.requests.clear()
        val again = run()

        assertEquals(1, server.listRequests())
        assertEquals(BoardResult("swissdevjobs", BoardOutcome.COMPLETED, unchanged = 3, skipped = 1), again)
    }

    @Test
    fun `a job that gets its text later is ingested on the next run`() {
        server.jobs = three() + job("partner-4", inFeed = false)
        run()
        server.jobs = three() + job("partner-4")

        val r = run()

        assertEquals(1, r.created)
        assertEquals(4, postings())
        assertNotNull(state.etag(sourceId))
    }

    @Test
    fun `a paused job is filtered out and an untitled job is skipped`() {
        server.jobs = three() + job("paused-5").copy(paused = true) + job("untitled-6", title = "")

        val r = run()

        assertEquals(BoardResult("swissdevjobs", BoardOutcome.COMPLETED, created = 3, skipped = 1, filteredOut = 1), r)
    }

    @Test
    fun `a changed description updates the posting`() {
        server.jobs = three()
        run()
        server.jobs = three().map { if (it.slug == "a-1") it.copy(description = "<p>New text</p>") else it }

        val r = run()

        assertEquals(1, r.updated)
        assertEquals(2, r.unchanged)
    }

    @Test
    fun `a robots txt that disallows the list blocks the board without requests to it`() {
        server.jobs = three()
        server.robotsBody = "User-agent: *\nDisallow: /api/\n"

        val r = run()

        assertEquals(BoardOutcome.BLOCKED_BY_ROBOTS, r.outcome)
        assertEquals(0, server.listRequests())
        assertEquals(0, server.feedRequests())
    }

    @Test
    fun `a robots txt that disallows the feed blocks the board`() {
        server.jobs = three()
        server.robotsBody = "User-agent: *\nDisallow: /rss\n"

        assertEquals(BoardOutcome.BLOCKED_BY_ROBOTS, run().outcome)
        assertEquals(0, server.listRequests())
    }

    @Test
    fun `an unreadable robots txt fails without fetching content`() {
        server.robotsStatus = 500

        assertEquals(BoardOutcome.FAILED, run().outcome)
        assertEquals(listOf("GET /robots.txt"), server.requests.toList())
    }

    @Test
    fun `a missing robots txt allows fetching`() {
        server.jobs = three()
        server.robotsStatus = 404

        assertEquals(BoardOutcome.COMPLETED, run().outcome)
    }

    @Test
    fun `an unreachable board fails and keeps the stored validator`() {
        server.jobs = three()
        run()
        val stored = state.etag(sourceId)
        server.listStatus = 500

        val r = run()

        assertEquals(BoardOutcome.FAILED, r.outcome)
        assertEquals("HTTP status 500", r.reason)
        assertEquals(stored, state.etag(sourceId))
    }

    @Test
    fun `a closed server fails with a fixed reason`() {
        val c = crawl()
        server.close()

        val r = run(c)

        assertEquals(BoardOutcome.FAILED, r.outcome)
        assertEquals("robots.txt unreadable", r.reason)
    }

    @Test
    fun `a malformed list fails and nothing is ingested`() {
        server.jobs = three()
        server.listBody = """{"jobs":[]}"""

        val r = run()

        assertEquals(BoardResult("swissdevjobs", BoardOutcome.FAILED, reason = "malformed list"), r)
        assertEquals(0, postings())
    }

    @Test
    fun `a malformed feed fails and nothing is ingested`() {
        server.jobs = three()
        server.feedBody = "<rss><channel>"

        val r = run()

        assertEquals(BoardResult("swissdevjobs", BoardOutcome.FAILED, reason = "malformed feed"), r)
        assertEquals(0, postings())
        assertNull(state.etag(sourceId))
    }

    @Test
    fun `a feed with an external entity is refused`() {
        server.jobs = three()
        server.feedBody = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><rss><channel><item><link>https://swissdevjobs.ch/jobs/a-1</link><description>&xxe;</description></item></channel></rss>"""

        val r = run()

        assertEquals("malformed feed", r.reason)
        assertEquals(0, postings())
    }

    @Test
    fun `a disabled adapter makes no request and has no result`() {
        server.jobs = three()

        val result = crawl(enabled = false).run()

        assertTrue(result.boards.isEmpty())
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `a board crawled again too soon is not requested`() {
        server.jobs = three()
        run(crawl(minInterval = Duration.ofHours(1)))
        server.requests.clear()

        val r = run(crawl(minInterval = Duration.ofHours(1)))

        assertEquals(BoardOutcome.TOO_SOON, r.outcome)
        assertTrue(server.requests.isEmpty(), server.requests.toString())
    }

    @Test
    fun `a job already stored from another source gets a second source and no second posting`() {
        val title = "Dedupe Engineer " + UUID.randomUUID()
        ingest.ingest(
            PostingCandidate(
                sourceId = "greenhouse-dedupetest", sourcePostingId = "1", url = "https://example.org/1", title = title,
                company = company, description = "Other text", rawPayload = "{}", city = "Zug", canton = "ZG",
            ),
        )
        server.jobs = listOf(job("dup-1", title, "Zug"))

        val r = run()

        assertEquals(1, r.created)
        val postings = jdbc.queryForObject("select count(*) from posting where title = ?", Int::class.java, title)!!
        val sources = jdbc.queryForObject(
            "select count(*) from posting_source ps join posting p on p.id = ps.posting_id where p.title = ?",
            Int::class.java, title,
        )!!
        assertEquals(1, postings)
        assertEquals(2, sources)
    }

    @Test
    fun `job text and response bodies never reach the logs`() {
        val marker = "SDJ-MARKER-7c21"
        server.jobs = listOf(
            Job("m-1", "Engineer $marker", "Co $marker", "Zug", description = "<p>Text $marker</p>"),
            Job("m-2", "Other $marker", "Co $marker", "Bern", inFeed = false),
        )

        val events = capture {
            run()
            server.listStatus = 500
            server.listBody = "FAIL $marker"
            run()
            server.listStatus = null
            server.listBody = "<html>$marker</html>"
            server.listContentType = "text/html"
            run()
            server.listBody = null
            server.feedBody = "<rss>$marker"
            run()
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val relevant = events.filter { event -> frameworkBodyPrinters.none { event.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { it.fullText() }
        assertTrue(relevant.any { "SwissDevJobs:" in it.formattedMessage }, "the capture saw no crawl logs, so it proves nothing")
        assertTrue(marker !in logged, "job text or a response body appears in the logs")
        assertFalse("m-1" in logged, "a job URL appears in the logs")
    }
}
