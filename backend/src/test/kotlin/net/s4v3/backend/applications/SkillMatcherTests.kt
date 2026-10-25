package net.s4v3.backend.applications

import net.s4v3.backend.applications.internal.SkillMatcher
import net.s4v3.backend.applications.internal.StoredAsked
import net.s4v3.backend.llm.SkillImportance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillMatcherTests {

    private fun asked(name: String, importance: SkillImportance = SkillImportance.REQUIRED, vararg related: String) =
        StoredAsked(name, importance, related.toList())

    @Test
    fun `case and punctuation do not matter`() {
        val m = SkillMatcher.match(listOf(asked("NODE.JS"), asked("ci / cd"), asked("Spring-Boot")), listOf("nodejs", "CI/CD", "Spring Boot"))
        assertEquals(listOf(0, 1, 2), m.include.map { it.profileIndex })
        assertTrue(m.gaps.isEmpty())
        assertTrue(m.other.isEmpty())
    }

    @Test
    fun `the alias table matches synonyms`() {
        val m = SkillMatcher.match(
            listOf(asked("Postgres"), asked("K8s"), asked("JS"), asked("Golang")),
            listOf("PostgreSQL", "Kubernetes", "JavaScript", "Go"),
        )
        assertEquals(listOf("PostgreSQL", "Kubernetes", "JavaScript", "Go"), m.include.map { it.profileName })
        assertEquals(listOf("Postgres", "K8s", "JS", "Golang"), m.include.map { it.name })
    }

    @Test
    fun `c and c plus plus are different skills`() {
        val m = SkillMatcher.match(listOf(asked("C++")), listOf("C"))
        assertEquals(listOf("C++"), m.gaps.map { it.name })
        assertEquals(listOf("C"), m.other.map { it.name })
    }

    @Test
    fun `a skill the profile does not name is a gap and the model cannot move it`() {
        // The model may "claim" a match through its related list, but only a name decides.
        val m = SkillMatcher.match(listOf(asked("Kafka", SkillImportance.NICE_TO_HAVE, "Kotlin")), listOf("Kotlin"))
        assertTrue(m.include.isEmpty())
        assertEquals(SkillImportance.NICE_TO_HAVE, m.gaps.single().importance)
        assertEquals(listOf("Kotlin"), m.gaps.single().related.map { it.name })
        assertEquals(listOf("Kotlin"), m.other.map { it.name })
    }

    @Test
    fun `related names that are not exactly saved skills are dropped`() {
        val m = SkillMatcher.match(listOf(asked("Kafka", SkillImportance.REQUIRED, "RabbitMQ", "kotlin", "Kotlin")), listOf("Kotlin"))
        assertEquals(listOf("Kotlin" to 0), m.gaps.single().related.map { it.name to it.profileIndex })
    }

    @Test
    fun `other holds the saved skills that are not asked for and notes attach to gaps by name`() {
        val m = SkillMatcher.match(listOf(asked("Kotlin"), asked("Kafka")), listOf("Kotlin", "Postgres"), mapOf("Kafka" to "my note"))
        assertEquals(listOf("Postgres"), m.other.map { it.name })
        assertEquals("my note", m.gaps.single().note)
    }
}
