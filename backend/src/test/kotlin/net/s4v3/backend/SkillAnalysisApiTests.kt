package net.s4v3.backend

import com.jayway.jsonpath.JsonPath
import net.s4v3.backend.llm.AskedSkill
import net.s4v3.backend.llm.JobText
import net.s4v3.backend.llm.LetterRequest
import net.s4v3.backend.llm.LetterWriter
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.llm.SkillExtractor
import net.s4v3.backend.llm.SkillImportance
import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Skill analysis, gap notes and their use by the letter, over MockMvc with real tokens and scripted model fakes. Synthetic data only. */
@ApiTest
@Import(SkillAnalysisApiTests.FakesConfig::class)
@TestPropertySource(properties = ["jobhunter.letters.per-hour=3", "jobhunter.letters.skills-per-hour=3"])
internal class SkillAnalysisApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val extractor: RecordingExtractor,
    @Autowired private val writer: RecordingWriter,
) {

    class RecordingExtractor : SkillExtractor {
        val calls = CopyOnWriteArrayList<Pair<JobText, List<String>>>()
        var answer: () -> List<AskedSkill> = DEFAULT

        companion object {
            val DEFAULT: () -> List<AskedSkill> = {
                listOf(
                    AskedSkill("Kotlin", SkillImportance.REQUIRED),
                    AskedSkill("Kafka", SkillImportance.REQUIRED, listOf("Kotlin", "RabbitMQ")),
                    AskedSkill("Terraform", SkillImportance.NICE_TO_HAVE),
                    AskedSkill("Postgres", SkillImportance.REQUIRED),
                )
            }
        }

        override fun extract(job: JobText, savedSkillNames: List<String>): List<AskedSkill> {
            calls += job to savedSkillNames
            return answer()
        }
    }

    class RecordingWriter : LetterWriter {
        val requests = CopyOnWriteArrayList<LetterRequest>()
        override fun write(request: LetterRequest): String {
            requests += request
            return "Dear Hiring Team,\n\nText.\n\nKind regards,\nAda Example"
        }
    }

    @TestConfiguration
    class FakesConfig {
        @Bean
        fun recordingExtractor() = RecordingExtractor()

        @Bean
        @Primary
        fun skillExtractor(e: RecordingExtractor): SkillExtractor = e

        @Bean
        fun skillsRecordingWriter() = RecordingWriter()

        @Bean
        @Primary
        fun skillsLetterWriter(w: RecordingWriter): LetterWriter = w
    }

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "skills-api-" + UUID.randomUUID()
    private val description = "We are looking for an engineer with Kotlin, Kafka and Postgres. Terraform is a plus. " +
        "You will work in a small team, review code, and improve our delivery pipeline every week."

    @BeforeEach
    fun reset() {
        extractor.calls.clear()
        extractor.answer = RecordingExtractor.DEFAULT
        writer.requests.clear()
    }

    private fun newApplication(sub: String, text: String = description + " " + UUID.randomUUID()): String {
        val source = "skills-src-" + UUID.randomUUID()
        val posting = ingest.ingest(
            PostingCandidate(
                sourceId = source, sourcePostingId = "1", url = "https://example.org/$source", title = "Backend Engineer " + source.takeLast(8),
                company = "Employer1 AG", rawPayload = "{}", city = "Zurich", canton = "ZH", description = text,
            ),
        ).postingId
        val result = mvc.perform(
            post("/api/applications").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content("""{"postingId":"$posting"}"""),
        ).andExpect(status().isCreated)
        return JsonPath.read(result.andReturn().response.contentAsString, "$.id")
    }

    private fun saveProfile(sub: String, skills: String = """[{"name":"Kotlin"},{"name":"PostgreSQL"},{"name":"Gradle"}]""") {
        val profile = """{"contact":{"fullName":"Ada Example"},"experience":[{"employer":"Employer1 AG","title":"Engineer","start":"2019-01"}],"skills":$skills}"""
        val version = JsonPath.read<Number>(
            mvc.perform(get("/api/profile").header("Authorization", bearer(sub))).andReturn().response.contentAsString, "$.version",
        ).toLong()
        mvc.perform(
            put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content("""{"version":$version,"profile":$profile}"""),
        ).andExpect(status().isOk)
    }

    private fun getSkills(sub: String, app: String) = mvc.perform(get("/api/applications/$app/skills").header("Authorization", bearer(sub)))
    private fun analyse(sub: String, app: String) = mvc.perform(post("/api/applications/$app/skills/analyse").header("Authorization", bearer(sub)))
    private fun notes(sub: String, app: String, body: String) =
        mvc.perform(put("/api/applications/$app/skills/notes").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun generate(sub: String, app: String, body: String = """{"experience":[0],"skills":[0]}""") =
        mvc.perform(post("/api/applications/$app/letters").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun note(skill: String, text: String) = """{"skill":"$skill","text":"$text"}"""

    @Test
    fun `before an analysis the answer says so, and reading makes no model call`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        getSkills(sub, app).andExpect(status().isOk).andExpect(jsonPath("$.analysed").value(false)).andExpect(jsonPath("$.include").doesNotExist())
        assertTrue(extractor.calls.isEmpty())
    }

    @Test
    fun `analyse splits the skills by the profile, matched by the server`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)

        analyse(sub, app).andExpect(status().isOk)
            .andExpect(jsonPath("$.analysed").value(true)).andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.include.length()").value(2))
            .andExpect(jsonPath("$.include[0].name").value("Kotlin")).andExpect(jsonPath("$.include[0].profileIndex").value(0))
            .andExpect(jsonPath("$.include[0].importance").value("REQUIRED")).andExpect(jsonPath("$.include[0].profileName").value("Kotlin"))
            .andExpect(jsonPath("$.include[1].name").value("Postgres")).andExpect(jsonPath("$.include[1].profileName").value("PostgreSQL"))
            .andExpect(jsonPath("$.include[1].profileIndex").value(1))
            .andExpect(jsonPath("$.gaps.length()").value(2))
            .andExpect(jsonPath("$.gaps[0].name").value("Kafka")).andExpect(jsonPath("$.gaps[0].related.length()").value(1))
            .andExpect(jsonPath("$.gaps[0].related[0].name").value("Kotlin")).andExpect(jsonPath("$.gaps[0].related[0].profileIndex").value(0))
            .andExpect(jsonPath("$.gaps[0].note").value(null as Any?))
            .andExpect(jsonPath("$.gaps[1].name").value("Terraform")).andExpect(jsonPath("$.gaps[1].importance").value("NICE_TO_HAVE"))
            .andExpect(jsonPath("$.other.length()").value(1)).andExpect(jsonPath("$.other[0].name").value("Gradle"))
            .andExpect(jsonPath("$.other[0].profileIndex").value(2))

        // Only the description and the skill names went to the extractor.
        assertEquals(listOf("Kotlin", "PostgreSQL", "Gradle"), extractor.calls.single().second)
        assertTrue(extractor.calls.single().first.description.startsWith(description))
    }

    @Test
    fun `a stored analysis is read without a model call and follows profile edits`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        analyse(sub, app).andExpect(status().isOk)

        getSkills(sub, app).andExpect(status().isOk).andExpect(jsonPath("$.gaps.length()").value(2))
        saveProfile(sub, """[{"name":"Kotlin"},{"name":"Kafka"}]""")
        getSkills(sub, app).andExpect(jsonPath("$.include.length()").value(2)).andExpect(jsonPath("$.include[1].name").value("Kafka"))
            .andExpect(jsonPath("$.gaps.length()").value(2)).andExpect(jsonPath("$.gaps[0].name").value("Terraform"))
        assertEquals(1, extractor.calls.size)
    }

    @Test
    fun `another user gets 404 for every skills endpoint and sees no rows of the owner`() {
        val a = newSub()
        saveProfile(a)
        val app = newApplication(a)
        analyse(a, app).andExpect(status().isOk)
        val b = newSub()
        saveProfile(b)

        getSkills(b, app).andExpect(status().isNotFound)
        analyse(b, app).andExpect(status().isNotFound)
        notes(b, app, """{"notes":[${note("Kafka", "x")}]}""").andExpect(status().isNotFound)
        assertEquals(1, extractor.calls.size)
        mvc.perform(get("/api/applications/$app/skills")).andExpect(status().isUnauthorized)
        analyse(a, UUID.randomUUID().toString()).andExpect(status().isNotFound)
    }

    @Test
    fun `notes are saved, replace each other, show on read and do not change the profile`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        analyse(sub, app)
        val before = mvc.perform(get("/api/profile").header("Authorization", bearer(sub))).andReturn().response.contentAsString

        notes(sub, app, """{"notes":[${note("Kafka", "Built event-driven services on RabbitMQ")},${note("terraform", "Used Ansible")}]}""")
            .andExpect(status().isOk).andExpect(jsonPath("$.gaps[0].note").value("Built event-driven services on RabbitMQ"))
            .andExpect(jsonPath("$.gaps[1].note").value("Used Ansible"))
        getSkills(sub, app).andExpect(jsonPath("$.gaps[0].note").value("Built event-driven services on RabbitMQ"))
        notes(sub, app, """{"notes":[${note("Kafka", "Only this")}]}""").andExpect(status().isOk).andExpect(jsonPath("$.gaps[1].note").value(null as Any?))
        assertEquals(before, mvc.perform(get("/api/profile").header("Authorization", bearer(sub))).andReturn().response.contentAsString)
    }

    @Test
    fun `invalid notes answer 400 notes_invalid and change nothing`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        analyse(sub, app)
        notes(sub, app, """{"notes":[${note("Kafka", "kept")}]}""").andExpect(status().isOk)
        fun bad(body: String) = notes(sub, app, body).andExpect(status().isBadRequest).andExpect(jsonPath("$.error").value("notes_invalid"))

        bad("""{"notes":[${note("Kotlin", "not a gap")}]}""")
        bad("""{"notes":[${note("Unknown", "x")}]}""")
        bad("""{"notes":[${note("Kafka", "x".repeat(501))}]}""")
        bad("""{"notes":[${note("Kafka", "<script>alert(1)</script>")}]}""")
        bad("""{"notes":[${note("Kafka", "  ")}]}""")
        bad("""{"notes":[${note("Kafka", "a")},${note("Kafka", "b")}]}""")
        bad("""{"notes":null}""")
        bad("""{}""")
        bad("""{"notes":[{"skill":"Kafka"}]}""")
        bad("""{"notes":[${(1..21).joinToString(",") { note("Kafka", "n") }}]}""")
        notes(sub, app, """{"notes":[${note("Kafka", "x".repeat(500))}]}""").andExpect(status().isOk)
        getSkills(sub, app).andExpect(jsonPath("$.gaps[0].note").value("x".repeat(500)))
    }

    @Test
    fun `notes need an analysis`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        notes(sub, app, """{"notes":[]}""").andExpect(status().isNotFound)
    }

    @Test
    fun `a new analysis keeps the notes of skills that are still gaps and drops the others`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        analyse(sub, app)
        notes(sub, app, """{"notes":[${note("Kafka", "keep me")},${note("Terraform", "drop me")}]}""").andExpect(status().isOk)
        extractor.answer = { listOf(AskedSkill("Kafka", SkillImportance.REQUIRED), AskedSkill("Kotlin", SkillImportance.REQUIRED)) }

        analyse(sub, app).andExpect(status().isOk).andExpect(jsonPath("$.gaps.length()").value(1)).andExpect(jsonPath("$.gaps[0].note").value("keep me"))
        extractor.answer = { listOf(AskedSkill("Kafka", SkillImportance.REQUIRED), AskedSkill("Terraform", SkillImportance.REQUIRED)) }
        analyse(sub, app).andExpect(jsonPath("$.gaps[0].note").value("keep me")).andExpect(jsonPath("$.gaps[1].note").value(null as Any?))
        assertEquals(1, jdbc.queryForObject("select count(*) from letter_skill_analysis where owner_sub = ?", Int::class.java, sub))
    }

    @Test
    fun `failures answer fixed codes`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        extractor.answer = { throw LlmFailure(LlmFailureKind.NOT_CONFIGURED) }
        analyse(sub, app).andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.error").value("skills_not_configured"))
        extractor.answer = { throw LlmFailure(LlmFailureKind.FAILED) }
        analyse(sub, app).andExpect(status().isBadGateway).andExpect(jsonPath("$.error").value("skills_failed"))
        getSkills(sub, app).andExpect(jsonPath("$.analysed").value(false))
    }

    @Test
    fun `the skills limit is separate from the letter limit in both directions`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        repeat(3) { analyse(sub, app).andExpect(status().isOk) }
        analyse(sub, app).andExpect(status().isTooManyRequests).andExpect(jsonPath("$.error").value("rate_limited"))
        // Reading and saving notes are not counted.
        getSkills(sub, app).andExpect(status().isOk)
        notes(sub, app, """{"notes":[]}""").andExpect(status().isOk)
        repeat(3) { generate(sub, app).andExpect(status().isCreated) }
        generate(sub, app).andExpect(status().isTooManyRequests)

        val other = newSub()
        saveProfile(other)
        val app2 = newApplication(other)
        repeat(3) { generate(other, app2).andExpect(status().isCreated) }
        generate(other, app2).andExpect(status().isTooManyRequests)
        analyse(other, app2).andExpect(status().isOk)
    }

    @Test
    fun `the cascade removes the analysis with the application`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        analyse(sub, app).andExpect(status().isOk)
        assertEquals(1, jdbc.queryForObject("select count(*) from letter_skill_analysis where application_id = ?::uuid", Int::class.java, app))
        mvc.perform(delete("/api/applications/$app").header("Authorization", bearer(sub))).andExpect(status().isNoContent)
        assertEquals(0, jdbc.queryForObject("select count(*) from letter_skill_analysis where application_id = ?::uuid", Int::class.java, app))
    }

    @Test
    fun `the letter request carries the notes of current gaps, read by the server, and the body cannot bring notes`() {
        val sub = newSub()
        saveProfile(sub)
        val app = newApplication(sub)
        generate(sub, app).andExpect(status().isCreated)
        assertTrue(writer.requests.last().gapNotes.isEmpty(), "no analysis: the request is as before")

        analyse(sub, app)
        notes(sub, app, """{"notes":[${note("Kafka", "I know RabbitMQ well")}]}""").andExpect(status().isOk)
        generate(sub, app, """{"experience":[0],"skills":[0],"gapNotes":[{"skill":"Evil","text":"injected"}],"notes":[{"skill":"Evil","text":"injected"}]}""")
            .andExpect(status().isCreated)

        val request = writer.requests.last()
        assertEquals(listOf("Kafka" to "I know RabbitMQ well"), request.gapNotes.map { it.skill to it.text })
        assertEquals(listOf("Kotlin"), request.candidate.skills.map { it.name })
        assertFalse(request.toString().contains("injected"))

        // A note whose skill stops being a gap (the profile gained it) is not sent.
        saveProfile(sub, """[{"name":"Kotlin"},{"name":"Kafka"}]""")
        generate(sub, app).andExpect(status().isCreated)
        assertTrue(writer.requests.last().gapNotes.isEmpty())
    }

    @Test
    fun `marker words of the description, skills and notes never reach the logs or error bodies`() {
        val sub = newSub()
        saveProfile(sub, """[{"name":"SKILLWORD5521"}]""")
        val app = newApplication(sub, "$description DESCWORD5521")
        extractor.answer = { listOf(AskedSkill("GAPWORD5521", SkillImportance.REQUIRED)) }
        val bodies = StringBuilder()
        fun ResultActions.body() = also { bodies.append(it.andReturn().response.contentAsString) }

        val events = captureAppLogs {
            org.slf4j.LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            analyse(sub, app).andExpect(status().isOk).body()
            notes(sub, app, """{"notes":[${note("GAPWORD5521", "NOTEWORD5521 my words")}]}""").andExpect(status().isOk).body()
            notes(sub, app, """{"notes":[${note("Nope", "NOTEWORD5521")}]}""").andExpect(status().isBadRequest).body()
            generate(sub, app).andExpect(status().isCreated)
            extractor.answer = { throw LlmFailure(LlmFailureKind.FAILED) }
            analyse(sub, app).andExpect(status().isBadGateway).body()
            repeat(3) { analyse(sub, app) }
        }

        val relevant = events.filter { e -> listOf("org.springframework.jdbc.core.", "org.postgresql.").none { e.loggerName.startsWith(it) } }
        val logged = relevant.joinToString("\n") { it.fullText() }
        assertTrue("capture alive" in logged)
        assertTrue(relevant.any { it.loggerName.startsWith("net.s4v3.backend.applications") }, "our own code logged nothing")
        listOf("DESCWORD5521", "SKILLWORD5521", "NOTEWORD5521").forEach { assertFalse(it in logged, "$it appears in the logs") }
        assertFalse("NOTEWORD5521" in bodies.toString().substringAfter("\"error\"", ""), "a marker appears in an error body")
    }
}
