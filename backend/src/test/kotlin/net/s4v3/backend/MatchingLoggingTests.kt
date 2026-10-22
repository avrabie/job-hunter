package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import net.s4v3.backend.matching.internal.MatchEvaluator
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.profile.ProfileService
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@IntegrationTest
internal class MatchingLoggingTests(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val profiles: ProfileService,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val evaluator: MatchEvaluator,
) {

    private val support = MatchingTestSupport(jdbc, profiles, ingest)
    private val skillMarker = "SKILLMARKER7731"
    private val roleMarker = "RoleMarker7731"
    private val titleMarker = "TitleMarker7731"

    @Test
    fun `profile and posting text never reach the logs and no user id sits next to a score`() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previous = root.level
        root.level = Level.TRACE
        root.addAppender(appender)
        val user = support.newUser()
        try {
            support.saveProfile(user, role = roleMarker, skills = listOf(skillMarker))
            val posting = support.newPosting(title = "$titleMarker $roleMarker")
            MatchingTestSupport.await("the result") { support.match(user, posting) != null }
            evaluator.evaluateProfile(user)
            evaluator.evaluatePosting(posting)
        } finally {
            root.detachAppender(appender)
            root.level = previous
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val relevant = appender.list.filter { e -> frameworkBodyPrinters.none { e.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { e ->
            e.formattedMessage + "\n" + ((e.throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty())
        }
        assertTrue(relevant.any { "Evaluated posting" in it.formattedMessage }, "the capture saw no matching logs, so it proves nothing")
        for (marker in listOf(skillMarker, roleMarker, titleMarker)) assertFalse(marker in logged, "$marker appears in the logs")
        val matchingLines = relevant.filter { it.loggerName.contains("matching") }.joinToString("\n") { it.formattedMessage }
        assertFalse(user in matchingLines, "a user id appears in the matching logs")
    }
}
