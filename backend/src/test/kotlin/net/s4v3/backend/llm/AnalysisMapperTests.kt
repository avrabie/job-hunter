package net.s4v3.backend.llm

import net.s4v3.backend.llm.internal.AnalysisMapper
import net.s4v3.backend.llm.internal.ProjectAnalysisDraft
import net.s4v3.backend.llm.internal.TechnologyDraft
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.RepositoryDigest
import net.s4v3.backend.profile.TechCategory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnalysisMapperTests {

    private val now = Instant.parse("2026-10-24T12:00:00Z")

    private val digest = RepositoryDigest(
        provider = CodeProvider.GITHUB,
        fullName = "octocat/hello-world",
        readme = "# Hello",
        paths = listOf("README.md", "build.gradle.kts", "src/main/kotlin/App.kt"),
        files = mapOf("build.gradle.kts" to "plugins { kotlin(\"jvm\") }"),
        truncated = false,
    )

    private fun tech(name: String, path: String = "build.gradle.kts", category: String? = "FRAMEWORK") =
        TechnologyDraft(name = name, category = category, evidencePath = path, evidence = "shown in $path")

    private fun draft(vararg techs: TechnologyDraft, summary: String? = "A small greeting app.") =
        ProjectAnalysisDraft(summary = summary, architecture = "One module.", techStack = techs.toList())

    @Test
    fun `a valid answer keeps its technologies with their evidence`() {
        val analysis = AnalysisMapper.toAnalysis(draft(tech("Spring Boot"), tech("Kotlin", category = "LANGUAGE")), digest, now)

        assertEquals("A small greeting app.", analysis.summary)
        assertEquals("One module.", analysis.architecture)
        assertEquals(listOf("Spring Boot", "Kotlin"), analysis.techStack.map { it.name })
        assertEquals(TechCategory.LANGUAGE, analysis.techStack[1].category)
        assertEquals("build.gradle.kts", analysis.techStack[0].evidencePath)
        assertEquals("shown in build.gradle.kts", analysis.techStack[0].evidence)
        assertEquals(now, analysis.analyzedAt)
    }

    @Test
    fun `a cited path that is not in the tree drops only that technology`() {
        val analysis = AnalysisMapper.toAnalysis(
            draft(tech("Ghost", path = "src/Missing.kt"), tech("Kotlin", category = "LANGUAGE")),
            digest,
            now,
        )

        assertEquals(listOf("Kotlin"), analysis.techStack.map { it.name })
    }

    @Test
    fun `the README counts as a cited path and an unknown category becomes OTHER`() {
        val analysis = AnalysisMapper.toAnalysis(draft(tech("Hello", path = "README.md", category = "WIDGET")), digest, now)

        assertEquals("README.md", analysis.techStack.single().evidencePath)
        assertEquals(TechCategory.OTHER, analysis.techStack.single().category)
    }

    @Test
    fun `case-insensitive duplicates keep the first spelling`() {
        val analysis = AnalysisMapper.toAnalysis(draft(tech("Kotlin"), tech("kotlin")), digest, now)

        assertEquals(listOf("Kotlin"), analysis.techStack.map { it.name })
    }

    @Test
    fun `at most 20 technologies are kept`() {
        val many = (1..30).map { tech("Tech $it") }

        val analysis = AnalysisMapper.toAnalysis(draft(*many.toTypedArray()), digest, now)

        assertEquals(20, analysis.techStack.size)
        assertEquals("Tech 1", analysis.techStack.first().name)
    }

    @Test
    fun `a name longer than 60 characters or blank is left out`() {
        val analysis = AnalysisMapper.toAnalysis(draft(tech("x".repeat(61)), tech("   "), tech("Kotlin")), digest, now)

        assertEquals(listOf("Kotlin"), analysis.techStack.map { it.name })
    }

    @Test
    fun `the summary is cut at a sentence boundary within its limit`() {
        val summary = "a".repeat(1000) + ". " + "word ".repeat(300)

        val analysis = AnalysisMapper.toAnalysis(draft(summary = summary), digest, now)

        assertEquals("a".repeat(1000) + ".", analysis.summary)
    }

    @Test
    fun `an empty summary fails the call with the fixed error`() {
        val failure = assertFailsWith<LlmFailure> { AnalysisMapper.toAnalysis(draft(summary = "  "), digest, now) }

        assertEquals(LlmFailureKind.FAILED, failure.kind)
    }

    @Test
    fun `files are the README and the digest files and truncated is copied`() {
        val cut = digest.copy(truncated = true, files = mapOf("build.gradle.kts" to "x", "package.json" to "y"))

        val analysis = AnalysisMapper.toAnalysis(draft(), cut, now)

        assertEquals(listOf("README.md", "build.gradle.kts", "package.json"), analysis.files)
        assertTrue(analysis.truncated)
    }
}
