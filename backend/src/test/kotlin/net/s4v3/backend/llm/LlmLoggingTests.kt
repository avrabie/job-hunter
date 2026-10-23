package net.s4v3.backend.llm

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import net.s4v3.backend.IntegrationTest
import net.s4v3.backend.llm.internal.ExecutorSource
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CV text and model answers must never reach a log. The application context runs with its real logging configuration (so
 * `logging.level.ai.koog: OFF` is part of what is tested); the model is a fake that echoes a marker.
 */
@IntegrationTest
@Import(LlmLoggingTests.FakeModel::class)
@TestPropertySource(properties = ["jobhunter.llm.api-key=test-key"])
class LlmLoggingTests(@Autowired private val reader: CvReader) {

    @TestConfiguration
    internal class FakeModel {
        // Fails to parse every time, and carries the marker, like a model that answers with the CV's own words.
        @Bean
        @Primary
        internal fun executors() = ExecutorSource { FakePromptExecutor("""{"summary": "ANSWERMARKER9921 """, "still broken ANSWERMARKER9921") }
    }

    @Test
    fun `neither the CV text nor the model's answer reaches the logs`() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previous = root.level
        val ours = LoggerFactory.getLogger("net.s4v3") as Logger
        val ourPrevious = ours.level
        // The level the application runs at; our own code is captured at TRACE. The logger `ai.koog` keeps the level the
        // application configures, which is what must hold.
        root.level = Level.INFO
        ours.level = Level.TRACE
        root.addAppender(appender)
        try {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            assertFailsWith<LlmFailure> { reader.profileFrom("Jane Doe worked at CVMARKER4417 as an engineer") }
        } finally {
            root.detachAppender(appender)
            root.level = previous
            ours.level = ourPrevious
        }

        val logged = appender.list.joinToString("\n") { e ->
            e.formattedMessage + "\n" + ((e.throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty())
        }
        assertTrue("capture alive" in logged, "the capture does not work, so it proves nothing")
        assertTrue("CV reading failed" in logged, "our own outcome line is expected")
        assertFalse("CVMARKER4417" in logged, "the CV text appears in the logs")
        assertFalse("ANSWERMARKER9921" in logged, "the model's answer appears in the logs")
    }
}
