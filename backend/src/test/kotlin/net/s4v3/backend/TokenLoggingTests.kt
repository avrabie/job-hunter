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
import java.time.Instant
import kotlin.test.assertTrue

@ApiTest
class TokenLoggingTests(@Autowired private val mvc: MockMvc) {

    private val issuer get() = SharedMockIssuer.instance

    @Test
    fun `rejected token values never appear in the logs, even at trace level`() {
        val wrongIssuer = issuer.token("user-1", issuer = "http://evil.example/auth")
        val expired = issuer.token("user-1", expiresAt = Instant.now().minusSeconds(600))
        val tampered = issuer.token("user-1").let { it.dropLast(8) + "AAAAAAAA" }
        val malformed = "this-is-not-a-jwt-but-looks-secret-12345"
        val rejected = listOf(wrongIssuer, expired, tampered, malformed)

        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val previousLevel = root.level
        root.level = Level.TRACE
        root.addAppender(appender)
        try {
            rejected.forEach { token ->
                mvc.perform(get("/api/test/ping").header("Authorization", "Bearer $token"))
                    .andExpect(status().isUnauthorized)
            }
        } finally {
            root.detachAppender(appender)
            root.level = previousLevel
        }

        val logged = appender.list.joinToString("\n") { event ->
            event.formattedMessage + "\n" + ((event.throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty())
        }
        assertTrue(appender.list.isNotEmpty(), "the capture saw no log events at all, so it proves nothing")
        rejected.forEach { token ->
            assertTrue(token !in logged, "a rejected token appears in the logs")
            // Also the signature part, in case only a fragment is logged.
            token.substringAfterLast('.').takeIf { it.length > 20 }?.let { signature ->
                assertTrue(signature !in logged, "a rejected token's signature appears in the logs")
            }
        }
    }
}
