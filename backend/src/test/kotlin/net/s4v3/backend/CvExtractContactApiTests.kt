package net.s4v3.backend

import com.jayway.jsonpath.JsonPath
import net.s4v3.backend.llm.CvReader
import net.s4v3.backend.llm.FakePromptExecutor
import net.s4v3.backend.llm.ProfileSection
import net.s4v3.backend.llm.internal.ExecutorSource
import net.s4v3.backend.llm.internal.KoogCvReader
import net.s4v3.backend.llm.internal.LlmSettings
import net.s4v3.backend.profile.Profile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `section=contact` through the real [KoogCvReader] on a scripted model: the grounding runs, Anthropic is never called. */
@ApiTest
@ContextConfiguration(initializers = [MockIssuerInitializer::class, FilesVolumeInitializer::class])
@Import(CvExtractContactApiTests.KoogReaderConfig::class)
@TestPropertySource(properties = ["jobhunter.cv.extractions-per-hour=3", "jobhunter.cv.max-text-chars=2000"])
internal class CvExtractContactApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val holder: ReaderHolder,
) {

    class ReaderHolder {
        var reader: CvReader = unused()
        var executor = FakePromptExecutor("{}")

        fun use(vararg answers: String, key: String = "test-key") {
            executor = FakePromptExecutor(*answers)
            reader = KoogCvReader(LlmSettings(apiKey = key, model = "Sonnet_5"), ExecutorSource { executor })
        }

        private fun unused(): CvReader = object : CvReader {
            override fun profileFrom(text: String): Profile = error("not used")
            override fun sectionFrom(text: String, section: ProfileSection): Profile = error("not used")
        }
    }

    @TestConfiguration
    class KoogReaderConfig {
        @Bean
        fun readerHolder() = ReaderHolder()

        @Bean
        @Primary
        fun cvReader(holder: ReaderHolder): CvReader = object : CvReader {
            override fun profileFrom(text: String) = holder.reader.profileFrom(text)
            override fun sectionFrom(text: String, section: ProfileSection) = holder.reader.sectionFrom(text, section)
        }
    }

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "cv-contact-" + UUID.randomUUID()

    @BeforeEach
    fun reset() = holder.use("{}")

    private fun upload(sub: String, text: String) {
        val result = mvc.perform(
            multipart("/api/files")
                .file(MockMultipartFile("file", "cv.txt", "application/octet-stream", text.toByteArray()))
                .param("kind", "CV")
                .header("Authorization", bearer(sub)),
        ).andExpect(status().isOk).andReturn()
        val id: String = JsonPath.read(result.response.contentAsString, "$.id")
        mvc.perform(
            put("/api/files/master").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content("""{"fileId":"$id"}"""),
        ).andExpect(status().isOk)
    }

    private fun contact(sub: String, section: String = "contact") =
        mvc.perform(post("/api/cv/extract").param("section", section).header("Authorization", bearer(sub)))

    private val cv = "Ada Example\nSoftware Engineer\nada@example.com, +41 79 123 45 67, linkedin.com/in/ada-example"

    @Test
    fun `contact is returned from the CV with every list empty`() {
        val sub = newSub()
        upload(sub, cv)
        holder.use("""{"fullName":"Ada Example","email":"ada@example.com","phone":"+41 79 123 45 67","linkedin":"linkedin.com/in/ada-example"}""")

        contact(sub, "Contact").andExpect(status().isOk)
            .andExpect(jsonPath("$.contact.fullName").value("Ada Example"))
            .andExpect(jsonPath("$.contact.email").value("ada@example.com"))
            .andExpect(jsonPath("$.contact.phone").value("+41 79 123 45 67"))
            .andExpect(jsonPath("$.contact.linkedin").value("https://linkedin.com/in/ada-example"))
            .andExpect(jsonPath("$.contact.website").doesNotExist())
            .andExpect(jsonPath("$.experience.length()").value(0))
            .andExpect(jsonPath("$.skills.length()").value(0))
    }

    @Test
    fun `an invented email is dropped`() {
        val sub = newSub()
        upload(sub, cv)
        holder.use("""{"fullName":"Ada Example","email":"invented@example.com"}""")

        contact(sub).andExpect(status().isOk)
            .andExpect(jsonPath("$.contact.fullName").value("Ada Example"))
            .andExpect(jsonPath("$.contact.email").doesNotExist())
    }

    @Test
    fun `an unknown section is still 400 and the model is not called`() {
        val sub = newSub()
        upload(sub, cv)

        contact(sub, "hobbies").andExpect(status().isBadRequest)
        contact(sub, "projects").andExpect(status().isBadRequest)

        assertTrue(holder.executor.prompts.isEmpty())
    }

    @Test
    fun `the same error answers as the other sections`() {
        val sub = newSub()
        contact(sub).andExpect(status().isConflict)

        upload(sub, "a".repeat(2001))
        contact(sub).andExpect(status().isContentTooLarge)

        val other = newSub()
        upload(other, cv)
        holder.use("not json", "still not json", "nope")
        contact(other).andExpect(status().isBadGateway)
        holder.use("{}", key = "")
        contact(other).andExpect(status().isServiceUnavailable)
        holder.use("{}")
        contact(other).andExpect(status().isOk)
        contact(other).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `a unique email never reaches a log line`() {
        val sub = newSub()
        val marker = "marker4417@example.com"
        upload(sub, "Ada Example $marker")
        holder.use("""{"fullName":"Ada Example","email":"$marker"}""")

        val events = captureAppLogs {
            contact(sub).andExpect(status().isOk).andExpect(jsonPath("$.contact.email").value(marker))
        }

        val logged = events.joinToString("\n") { it.fullText() }
        assertTrue("CV extraction requested" in logged)
        assertFalse("marker4417" in logged, "the contact value appears in the logs")
        assertEquals(1, holder.executor.prompts.size)
    }
}
