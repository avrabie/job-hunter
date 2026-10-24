package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingRejectedException
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@IntegrationTest
class PostingLoggingTests(@Autowired private val ingest: PostingIngest) {

    private val marker = "POSTING-MARKER-9c41"

    private fun candidate(description: String, payload: String, title: String = "Kotlin Developer") = PostingCandidate(
        sourceId = "log-" + UUID.randomUUID(), sourcePostingId = "1", url = "https://example.org/1", title = title,
        company = "Logco " + UUID.randomUUID(), description = description, rawPayload = payload, city = "Zurich",
    )

    @Test
    fun `posting text and payloads never reach the logs on create, update or rejection`() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val capture = LogCapture(root)
        val previousLevel = root.level
        root.level = Level.TRACE
        try {
            val c = candidate("We are looking for a backend engineer. $marker", """{"text":"$marker"}""")
            ingest.ingest(c)
            ingest.ingest(c.copy(description = "Changed description for the engineer role. $marker", rawPayload = "$marker v2"))
            assertFailsWith<PostingRejectedException> { ingest.ingest(c.copy(title = " ")) }
        } finally {
            capture.close()
            root.level = previousLevel
        }

        // Spring JDBC and the PostgreSQL driver print bound SQL parameters at TRACE; they are off at the INFO level the
        // application runs at, and the rule is never to enable them. The test covers everything else.
        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val relevant = capture.events().filter { event -> frameworkBodyPrinters.none { event.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { event ->
            event.fullText()
        }
        assertTrue(relevant.isNotEmpty(), "the capture saw no log events at all, so it proves nothing")
        assertTrue(marker !in logged, "posting text appears in the logs")
    }
}
