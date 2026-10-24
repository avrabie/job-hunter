package net.s4v3.backend

import net.s4v3.backend.FakeGreenhouse.Job
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.sources.internal.FetchStateStore
import net.s4v3.backend.sources.internal.GreenhouseCrawl
import net.s4v3.backend.sources.internal.PoliteHttp
import net.s4v3.backend.sources.internal.SourcesSettings
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertTrue

@IntegrationTest
internal class SourcesLoggingTests(
    @Autowired private val state: FetchStateStore,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val dataSource: DataSource,
) {

    private val marker = "SOURCE-MARKER-5e17"

    @Test
    fun `job text and response bodies never reach the logs`() {
        FakeGreenhouse().use { server ->
            val company = "Logsrc " + UUID.randomUUID()
            val done = "d" + UUID.randomUUID().toString().replace("-", "").take(10)
            val partial = "p" + UUID.randomUUID().toString().replace("-", "").take(10)
            val broken = "b" + UUID.randomUUID().toString().replace("-", "").take(10)
            val html = "h" + UUID.randomUUID().toString().replace("-", "").take(10)
            server.boards[done] = listOf(Job(1, "Backend Engineer $marker", "Zurich", "&lt;p&gt;Text $marker&lt;/p&gt;", company))
            server.boards[partial] = listOf(Job(2, "Platform Engineer $marker", "Bern", "&lt;p&gt;Text $marker&lt;/p&gt;", company))
            server.boards[broken] = listOf(Job(3, "Data Engineer", "Basel", company = company))
            server.boards[html] = listOf(Job(4, "Data Engineer", "Basel", company = company))
            server.detailStatus[2] = 500
            server.detailBody = "FAIL $marker"
            val settings = SourcesSettings(
                pause = Duration.ZERO, minInterval = Duration.ZERO, readTimeout = Duration.ofSeconds(3),
                greenhouse = SourcesSettings.Greenhouse(baseUrl = server.baseUrl, boards = listOf(done, partial, broken, html)),
            )
            val crawl = GreenhouseCrawl(settings, PoliteHttp(settings), state, ingest, dataSource)

            val events = capture {
                crawl.run()
                server.listStatus = 500
                server.listBody = "FAIL $marker"
                crawl.run()
                server.listStatus = null
                server.listBody = "<html>$marker</html>"
                server.listContentType = "text/html"
                crawl.run()
            }

            val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
            val relevant = events.filter { event -> frameworkBodyPrinters.none { event.loggerName.startsWith(it) } }
            val logged = relevant.joinToString("\n") { event ->
                event.fullText()
            }
            assertTrue(relevant.any { "Greenhouse board" in it.formattedMessage }, "the capture saw no crawl logs, so it proves nothing")
            assertTrue(marker !in logged, "job text or a response body appears in the logs")
        }
    }
}
