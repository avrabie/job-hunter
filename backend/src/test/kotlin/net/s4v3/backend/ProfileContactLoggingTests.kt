package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertTrue

@ApiTest
class ProfileContactLoggingTests(@Autowired private val mvc: MockMvc) {

    private val marker = "contact-marker-7f3a@example.org"

    private fun fields(email: String) = """
        {"contact":{"fullName":"Ada Example","email":"$email","phone":"+41 79 000 00 00"},"summary":"ok"}
    """.trimIndent()

    @Test
    fun `contact data never reaches the logs on save or validation error`() {
        val token = "Bearer " + SharedMockIssuer.instance.token("contact-log-user-" + UUID.randomUUID())
        fun send(body: String) =
            mvc.perform(put("/api/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))

        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previousLevel = root.level
        root.level = Level.TRACE
        root.addAppender(appender)
        try {
            send("""{"version":0,"profile":${fields(marker)}}""").andExpect(status().isOk)
            send("""{"version":1,"profile":${fields("$marker x")}}""").andExpect(status().isBadRequest)
        } finally {
            root.detachAppender(appender)
            root.level = previousLevel
        }

        // Spring MVC, Spring JDBC and the PostgreSQL driver print bodies and bound parameters at DEBUG or TRACE; they are left
        // out here, as in ProfileLoggingTests, and the rule is that they are never enabled.
        val frameworkBodyPrinters = listOf("org.springframework.web.", "org.springframework.jdbc.core.", "org.postgresql.")
        val relevant = appender.list.filter { event -> frameworkBodyPrinters.none { event.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { event ->
            event.formattedMessage + "\n" + ((event.throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty())
        }
        assertTrue(relevant.isNotEmpty(), "the capture saw no log events at all, so it proves nothing")
        assertTrue(marker !in logged, "contact data appears in the logs")
    }
}
