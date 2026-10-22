package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ApiTest
class PostingsLoggingTests(@Autowired private val mvc: MockMvc) {

    @Test
    fun `the search text never reaches the logs`() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previous = root.level
        // The level the application runs at; our own code is captured at TRACE. Spring prints request URLs (and so the
        // query) at DEBUG and below, which the README forbids enabling in the cluster.
        root.level = Level.INFO
        val ours = LoggerFactory.getLogger("net.s4v3") as Logger
        val ourPrevious = ours.level
        ours.level = Level.TRACE
        root.addAppender(appender)
        try {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            mvc.perform(get("/api/postings?q=SEARCHMARKER4417").header("Authorization", "Bearer " + SharedMockIssuer.instance.token("log-user")))
                .andExpect(status().isOk)
        } finally {
            root.detachAppender(appender)
            root.level = previous
            ours.level = ourPrevious
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val relevant = appender.list.filter { e -> frameworkBodyPrinters.none { e.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { e ->
            e.formattedMessage + "\n" + ((e.throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty())
        }
        assertTrue("capture alive" in logged, "the capture does not work, so it proves nothing")
        assertFalse("SEARCHMARKER4417" in logged, "the search text appears in the logs")
    }
}
