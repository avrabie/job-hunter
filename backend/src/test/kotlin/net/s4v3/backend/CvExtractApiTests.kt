package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.read.ListAppender
import net.s4v3.backend.llm.CvReader
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.Skill
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The model is a scripted fake: these tests are about the CV endpoints around it, never about Anthropic. */
@ApiTest
@Import(CvExtractApiTests.ScriptedReaderConfig::class)
@TestPropertySource(properties = ["jobhunter.cv.extractions-per-hour=2", "jobhunter.cv.max-text-chars=2000"])
internal class CvExtractApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val reader: ScriptedReader,
) {

    class ScriptedReader : CvReader {
        val received = mutableListOf<String>()
        var answer: (String) -> Profile = { Profile(skills = listOf(Skill("Kotlin"))) }

        override fun profileFrom(text: String): Profile {
            received += text
            return answer(text)
        }
    }

    @TestConfiguration
    class ScriptedReaderConfig {
        @Bean
        fun scriptedReader() = ScriptedReader()

        @Bean
        @Primary
        fun cvReader(reader: ScriptedReader): CvReader = reader
    }

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "cv-extract-" + UUID.randomUUID()

    @BeforeEach
    fun reset() {
        reader.received.clear()
        reader.answer = { Profile(skills = listOf(Skill("Kotlin"))) }
    }

    private fun upload(sub: String, name: String, bytes: ByteArray) =
        mvc.perform(multipart("/api/cv").file(MockMultipartFile("file", name, "application/octet-stream", bytes)).header("Authorization", bearer(sub)))
            .andExpect(status().isOk)

    private fun extract(sub: String) = mvc.perform(post("/api/cv/extract").header("Authorization", bearer(sub)))

    private fun pdfWithText(text: String?): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            if (text != null) {
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    cs.newLineAtOffset(50f, 700f)
                    cs.showText(text)
                    cs.endText()
                }
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    @Test
    fun `a draft is returned and the profile is untouched`() {
        val sub = newSub()
        upload(sub, "cv.txt", "Jane Doe, Kotlin engineer".toByteArray())

        extract(sub).andExpect(status().isOk).andExpect(jsonPath("$.skills[0].name").value("Kotlin"))

        assertEquals(listOf("Jane Doe, Kotlin engineer"), reader.received)
        mvc.perform(get("/api/profile").header("Authorization", bearer(sub)))
            .andExpect(status().isOk).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.profile.skills.length()").value(0))
    }

    @Test
    fun `no token gets 401 and no CV gets 404`() {
        mvc.perform(post("/api/cv/extract")).andExpect(status().isUnauthorized)
        extract(newSub()).andExpect(status().isNotFound)
        assertTrue(reader.received.isEmpty())
    }

    @Test
    fun `the text of a PDF is read and sent`() {
        val sub = newSub()
        upload(sub, "cv.pdf", pdfWithText("Kotlin engineer at Acme"))

        extract(sub).andExpect(status().isOk)

        assertTrue("Kotlin engineer at Acme" in reader.received.single())
    }

    @Test
    fun `a PDF without text is 422 and the model is not called`() {
        val sub = newSub()
        upload(sub, "scan.pdf", pdfWithText(null))

        extract(sub).andExpect(status().isUnprocessableEntity)

        assertTrue(reader.received.isEmpty())
    }

    @Test
    fun `a CV text over the limit is 413 and the model is not called`() {
        val sub = newSub()
        upload(sub, "long.txt", "a".repeat(2001).toByteArray())

        extract(sub).andExpect(status().isPayloadTooLarge)

        assertTrue(reader.received.isEmpty())
    }

    @Test
    fun `the third call within the hour is 429 and the model is not called again`() {
        val sub = newSub()
        upload(sub, "cv.txt", "text".toByteArray())

        extract(sub).andExpect(status().isOk)
        extract(sub).andExpect(status().isOk)
        extract(sub).andExpect(status().isTooManyRequests)

        assertEquals(2, reader.received.size)
        // The limit is per user.
        val other = newSub()
        upload(other, "cv.txt", "text".toByteArray())
        extract(other).andExpect(status().isOk)
    }

    @Test
    fun `a model failure is 502 with a fixed body and a missing key is 503`() {
        val sub = newSub()
        upload(sub, "cv.txt", "MODELMARKER4417 text".toByteArray())

        reader.answer = { throw LlmFailure(LlmFailureKind.FAILED) }
        val body = extract(sub).andExpect(status().isBadGateway).andReturn().response.contentAsString
        assertFalse("MODELMARKER4417" in body)

        reader.answer = { throw LlmFailure(LlmFailureKind.NOT_CONFIGURED) }
        extract(sub).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `each user's extraction reads only their own CV`() {
        val a = newSub()
        val b = newSub()
        upload(a, "a.txt", "ONLY-A".toByteArray())
        upload(b, "b.txt", "ONLY-B".toByteArray())

        extract(a).andExpect(status().isOk)
        extract(b).andExpect(status().isOk)

        assertEquals(listOf("ONLY-A", "ONLY-B"), reader.received)
    }

    @Test
    fun `CV content never reaches the logs through upload, extraction and delete`() {
        val sub = newSub()
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val ours = LoggerFactory.getLogger("net.s4v3") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val (previous, ourPrevious) = root.level to ours.level
        // The level the application runs at; our own code is captured at TRACE.
        root.level = Level.INFO
        ours.level = Level.TRACE
        root.addAppender(appender)
        reader.answer = { Profile(summary = "ANSWERMARKER4417 $it") }
        try {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            upload(sub, "CVNAMEMARKER4417.txt", "CVTEXTMARKER4417 engineer".toByteArray())
            extract(sub).andExpect(status().isOk)
            mvc.perform(delete("/api/cv").header("Authorization", bearer(sub))).andExpect(status().isNoContent)
        } finally {
            root.detachAppender(appender)
            root.level = previous
            ours.level = ourPrevious
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val logged = appender.list.filter { e -> frameworkBodyPrinters.none { e.loggerName.startsWith(it) } }
            .joinToString("\n") { e -> e.formattedMessage + "\n" + ((e.throwableProxy as? ThrowableProxy)?.throwable?.stackTraceToString().orEmpty()) }
        assertTrue("capture alive" in logged, "the capture does not work, so it proves nothing")
        assertTrue("CV extraction requested" in logged, "our own outcome line is expected")
        listOf("CVTEXTMARKER4417", "CVNAMEMARKER4417", "ANSWERMARKER4417").forEach {
            assertFalse(it in logged, "$it appears in the logs")
        }
    }
}
