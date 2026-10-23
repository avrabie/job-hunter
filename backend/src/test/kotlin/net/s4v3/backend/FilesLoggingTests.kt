package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The files flow over MockMvc with the log capture of `CvExtractApiTests`; the volume is temporary, as in `FilesApiTests`. */
@ApiTest
@ContextConfiguration(initializers = [MockIssuerInitializer::class, FilesVolumeInitializer::class])
internal class FilesLoggingTests(@Autowired private val mvc: MockMvc) {

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "files-log-" + UUID.randomUUID()

    @Test
    fun `file names, file content and download tickets never reach the logs`() {
        val sub = newSub()
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val ours = LoggerFactory.getLogger("net.s4v3") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val (previous, ourPrevious) = root.level to ours.level
        // The level the application runs at; our own code is captured at TRACE.
        root.level = Level.INFO
        ours.level = Level.TRACE
        root.addAppender(appender)
        var ticket = ""
        try {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            val upload = mvc.perform(
                multipart("/api/files").file(MockMultipartFile("file", "FILENAMEMARKER4417.txt", "text/plain", "FILEBYTESMARKER4417 notes".toByteArray()))
                    .param("kind", "OTHER")
                    .header("Authorization", bearer(sub)),
            ).andExpect(status().isOk).andReturn()
            val id: String = JsonPath.read(upload.response.contentAsString, "$.id")
            mvc.perform(get("/api/files").header("Authorization", bearer(sub))).andExpect(status().isOk)
            val ticketAnswer = mvc.perform(post("/api/files/$id/download-ticket").header("Authorization", bearer(sub)))
                .andExpect(status().isOk).andReturn()
            val path: String = JsonPath.read(ticketAnswer.response.contentAsString, "$.path")
            ticket = path.substringAfter("ticket=")
            mvc.perform(get(path)).andExpect(status().isOk)
            mvc.perform(delete("/api/files/$id").header("Authorization", bearer(sub))).andExpect(status().isNoContent)
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
        assertTrue(ticket.isNotEmpty(), "no ticket was issued")
        listOf("FILENAMEMARKER4417", "FILEBYTESMARKER4417", ticket).forEach {
            assertFalse(it in logged, "a file marker or ticket appears in the logs")
        }
        assertFalse("ticket=" in logged, "a query string with ticket= appears in the logs")
        assertTrue(relevant.any { it.loggerName.startsWith("net.s4v3.backend.files") }, "our own files code logged nothing")
    }
}
