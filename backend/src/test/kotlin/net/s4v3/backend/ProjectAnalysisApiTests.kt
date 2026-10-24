package net.s4v3.backend

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.llm.ProjectAnalyst
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.DigestResult
import net.s4v3.backend.profile.ProjectAnalysis
import net.s4v3.backend.profile.RepositoryDigest
import net.s4v3.backend.profile.RepositoryDigestSource
import net.s4v3.backend.profile.TechCategory
import net.s4v3.backend.profile.Technology
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The repository and the model are scripted fakes: these tests are about the analysis endpoint around them, never about
 * GitHub or Anthropic.
 */
@ApiTest
@Import(ProjectAnalysisApiTests.ScriptedConfig::class)
@TestPropertySource(properties = ["jobhunter.analysis.per-hour=2"])
internal class ProjectAnalysisApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val digests: ScriptedDigests,
    @Autowired private val analyst: ScriptedAnalyst,
) {

    class ScriptedDigests : RepositoryDigestSource {
        val calls = CopyOnWriteArrayList<String>()
        var answer: (CodeProvider, String) -> DigestResult = { _, _ -> DigestResult.Found(sampleDigest()) }

        override fun digest(provider: CodeProvider, fullName: String): DigestResult {
            calls += "${provider.name} $fullName"
            return answer(provider, fullName)
        }
    }

    class ScriptedAnalyst : ProjectAnalyst {
        val received = CopyOnWriteArrayList<RepositoryDigest>()
        var answer: (RepositoryDigest) -> ProjectAnalysis = { sampleAnalysis() }

        override fun analyze(digest: RepositoryDigest): ProjectAnalysis {
            received += digest
            return answer(digest)
        }
    }

    @TestConfiguration
    class ScriptedConfig {
        @Bean
        fun scriptedDigests() = ScriptedDigests()

        @Bean
        fun scriptedAnalyst() = ScriptedAnalyst()

        @Bean
        @Primary
        fun repositoryDigestSource(digests: ScriptedDigests): RepositoryDigestSource = digests

        @Bean
        @Primary
        fun projectAnalyst(analyst: ScriptedAnalyst): ProjectAnalyst = analyst
    }

    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)
    private fun newSub() = "analysis-" + UUID.randomUUID()

    @BeforeEach
    fun reset() {
        digests.calls.clear()
        digests.answer = { _, _ -> DigestResult.Found(sampleDigest()) }
        analyst.received.clear()
        analyst.answer = { sampleAnalysis() }
    }

    /** Saves a profile with the given `owner/repo` names as GitHub open source projects; the user's first save. */
    private fun save(sub: String, vararg fullNames: String): ResultActions {
        val projects = fullNames.joinToString(",") { name ->
            """{"provider":"GITHUB","fullName":"$name","name":"${name.substringAfter('/')}","url":"https://github.com/$name"}"""
        }
        return mvc.perform(
            put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":0,"profile":{"openSourceProjects":[$projects]}}"""),
        ).andExpect(status().isOk)
    }

    private fun analyze(sub: String, provider: String, fullName: String) =
        mvc.perform(
            post("/api/profile/open-source-projects/analyze").header("Authorization", bearer(sub))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"$provider","fullName":"$fullName"}"""),
        )

    @Test
    fun `a saved project is analysed and the stored profile and its version are unchanged`() {
        val sub = newSub()
        save(sub, "octocat/hello-world")

        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isOk)
            .andExpect(jsonPath("$.analysis.summary").value("A demo service."))
            .andExpect(jsonPath("$.analysis.architecture").value("One module."))
            .andExpect(jsonPath("$.analysis.techStack[0].name").value("Spring Boot"))
            .andExpect(jsonPath("$.analysis.techStack[0].category").value("FRAMEWORK"))
            .andExpect(jsonPath("$.analysis.techStack[0].evidencePath").value("build.gradle.kts"))
            .andExpect(jsonPath("$.analysis.techStack[0].evidence").value("spring-boot gradle plugin"))
            .andExpect(jsonPath("$.analysis.files[1]").value("build.gradle.kts"))
            .andExpect(jsonPath("$.analysis.truncated").value(false))
            .andExpect(jsonPath("$.analysis.analyzedAt").value("2026-10-24T12:00:00Z"))

        assertEquals(listOf("GITHUB octocat/hello-world"), digests.calls)
        assertEquals(listOf("build.gradle.kts"), analyst.received.single().files.keys.toList())
        mvc.perform(get("/api/profile").header("Authorization", bearer(sub)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.profile.openSourceProjects.length()").value(1))
    }

    @Test
    fun `a repository that is not in the caller's saved profile is 404 even if another user saved it`() {
        save(newSub(), "octocat/hello-world")

        analyze(newSub(), "GITHUB", "octocat/hello-world").andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("project_not_found"))

        assertTrue(digests.calls.isEmpty(), "GitHub was called")
        assertTrue(analyst.received.isEmpty(), "the model was called")
    }

    @Test
    fun `malformed input is 400 invalid_request and no outside call is made`() {
        val sub = newSub()
        save(sub, "octocat/hello-world")

        listOf(
            "GITHUB" to "../x",
            "GITHUB" to "a/b/c",
            "GITHUB" to "",
            "GITHUB" to "octocat/..",
            "NOPE" to "octocat/hello-world",
        ).forEach { (provider, fullName) ->
            analyze(sub, provider, fullName).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("invalid_request"))
        }

        assertTrue(digests.calls.isEmpty(), "GitHub was called")
        assertTrue(analyst.received.isEmpty(), "the model was called")
    }

    @Test
    fun `anonymous calls get 401`() {
        mvc.perform(
            post("/api/profile/open-source-projects/analyze").contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"GITHUB","fullName":"octocat/hello-world"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `the hourly limit answers 429 too_many_analyses before any outside call and is per user`() {
        val sub = newSub()
        save(sub, "octocat/hello-world")

        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isOk)
        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isOk)
        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("too_many_analyses"))

        assertEquals(2, digests.calls.size)
        assertEquals(2, analyst.received.size)

        // The allowance belongs to the user: another user with a saved project can still analyse.
        val other = newSub()
        save(other, "octocat/hello-world")
        analyze(other, "GITHUB", "octocat/hello-world").andExpect(status().isOk)
    }

    @Test
    fun `a repository that GitHub does not find answers 404 repository_not_found and the model is not called`() {
        val sub = newSub()
        save(sub, "octocat/gone")
        digests.answer = { _, _ -> DigestResult.NotFound }

        analyze(sub, "GITHUB", "octocat/gone").andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("repository_not_found"))

        assertTrue(analyst.received.isEmpty(), "the model was called")
    }

    @Test
    fun `a provider that is down or limited answers 502 or 429 and the model is not called`() {
        val sub = newSub()
        save(sub, "octocat/hello-world")

        digests.answer = { _, _ -> DigestResult.Unavailable }
        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.error").value("provider_unavailable"))

        digests.answer = { _, _ -> DigestResult.RateLimited }
        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("provider_rate_limited"))

        assertTrue(analyst.received.isEmpty(), "the model was called")
    }

    @Test
    fun `a missing model key answers 503 analysis_not_configured and an unusable answer 502 analysis_failed`() {
        val sub = newSub()
        save(sub, "octocat/hello-world")

        analyst.answer = { throw LlmFailure(LlmFailureKind.NOT_CONFIGURED) }
        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.error").value("analysis_not_configured"))

        analyst.answer = { throw LlmFailure(LlmFailureKind.FAILED) }
        analyze(sub, "GITHUB", "octocat/hello-world").andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.error").value("analysis_failed"))
    }

    @Test
    fun `repository text, the model's answer and the repository name never reach the logs`() {
        val sub = newSub()
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val ours = LoggerFactory.getLogger("net.s4v3") as Logger
        val capture = LogCapture(root)
        val (previous, ourPrevious) = root.level to ours.level
        // The level the application runs at; our own code is captured at TRACE.
        root.level = Level.INFO
        ours.level = Level.TRACE
        digests.answer = { provider, name ->
            DigestResult.Found(
                RepositoryDigest(
                    provider = provider,
                    fullName = name,
                    readme = "READMEMARKER4417 project text",
                    paths = listOf("README.md", "build.gradle.kts"),
                    files = mapOf("build.gradle.kts" to "TREEMARKER4417"),
                    truncated = false,
                ),
            )
        }
        analyst.answer = { sampleAnalysis(summary = "ANSWERMARKER4417 summary") }
        try {
            LoggerFactory.getLogger("net.s4v3.test").info("capture alive")
            save(sub, "octocat/FULLNAMEMARKER4417")
            analyze(sub, "GITHUB", "octocat/FULLNAMEMARKER4417").andExpect(status().isOk)
        } finally {
            capture.close()
            root.level = previous
            ours.level = ourPrevious
        }

        val frameworkBodyPrinters = listOf("org.springframework.jdbc.core.", "org.postgresql.")
        val logged = capture.events().filter { e -> frameworkBodyPrinters.none { e.loggerName.startsWith(it) } }
            .joinToString("\n") { e -> e.fullText() }
        assertTrue("capture alive" in logged, "the capture does not work, so it proves nothing")
        assertTrue("Project analysis finished" in logged, "our own outcome line is expected")
        listOf("READMEMARKER4417", "TREEMARKER4417", "ANSWERMARKER4417", "FULLNAMEMARKER4417").forEach {
            assertFalse(it in logged, "$it appears in the logs")
        }
    }

    private companion object {
        fun sampleDigest() = RepositoryDigest(
            provider = CodeProvider.GITHUB,
            fullName = "octocat/hello-world",
            readme = "# Hello",
            paths = listOf("README.md", "build.gradle.kts"),
            files = mapOf("build.gradle.kts" to "plugins { id(\"org.springframework.boot\") version \"3.4.0\" }"),
            truncated = false,
        )

        fun sampleAnalysis(summary: String = "A demo service.") = ProjectAnalysis(
            summary = summary,
            architecture = "One module.",
            techStack = listOf(
                Technology("Spring Boot", TechCategory.FRAMEWORK, "build.gradle.kts", "spring-boot gradle plugin"),
            ),
            files = listOf("README.md", "build.gradle.kts"),
            truncated = false,
            analyzedAt = Instant.parse("2026-10-24T12:00:00Z"),
        )
    }
}
