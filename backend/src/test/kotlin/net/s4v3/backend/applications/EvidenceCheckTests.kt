package net.s4v3.backend.applications

import net.s4v3.backend.applications.internal.EvidenceCheck
import net.s4v3.backend.applications.internal.EvidenceInputBuilder
import net.s4v3.backend.applications.internal.SkillMatcher
import net.s4v3.backend.applications.internal.StoredAsked
import net.s4v3.backend.llm.AskedEvidence
import net.s4v3.backend.llm.EvidenceKind
import net.s4v3.backend.llm.SkillImportance
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.Contact
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.OpenSourceProject
import net.s4v3.backend.profile.Preferences
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.Skill
import net.s4v3.backend.profile.WorkAuthorisation
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvidenceCheckTests {

    private fun job(vararg achievements: String, tech: List<String> = emptyList(), title: String = "Engineer") =
        Experience("Employer1 AG", title, YearMonth.of(2020, 1), achievements = achievements.toList(), technologies = tech)

    private fun project(name: String = "triage-bot", description: String? = null) =
        OpenSourceProject(CodeProvider.GITHUB, "o/$name", name, "https://example.org/$name", description = description)

    private fun check(exp: List<Experience> = emptyList(), projects: List<OpenSourceProject> = emptyList()) = EvidenceCheck(exp, projects)
    private fun claim(quote: String, index: Int = 0, kind: EvidenceKind = EvidenceKind.EXPERIENCE) = AskedEvidence(kind, index, quote)

    @Test
    fun `a real quote of an entry that names the skill passes with source and quote from the profile`() {
        val hit = assertNotNull(check(listOf(job("Built an  LLM-based triage assistant."))).check("AI", claim("built an llm-based TRIAGE assistant")))
        assertEquals("Engineer at Employer1 AG", hit.source)
        assertEquals("built an llm-based TRIAGE assistant", hit.quote)
        val p = assertNotNull(check(projects = listOf(project(description = "Kafka consumer for alerts"))).check("Kafka", claim("Kafka consumer", kind = EvidenceKind.PROJECT)))
        assertEquals("triage-bot", p.source)
    }

    @Test
    fun `a fabricated quote fails`() {
        assertNull(check(listOf(job("Built a Kafka pipeline"))).check("Kafka", claim("Built a Kafka platform for everything")))
    }

    @Test
    fun `a quote that occurs but whose entry does not name the skill fails`() {
        assertNull(check(listOf(job("Built a pipeline", "Wrote Kafka consumers"))).check("Terraform", claim("Built a pipeline")))
    }

    @Test
    fun `a stale or unknown index fails`() {
        val c = check(listOf(job("Built a Kafka pipeline")))
        assertNull(c.check("Kafka", claim("Kafka pipeline", index = 1)))
        assertNull(c.check("Kafka", claim("Kafka pipeline", index = -1)))
        assertNull(c.check("Kafka", claim("Kafka pipeline", kind = EvidenceKind.PROJECT)))
        assertNull(c.check("Kafka", null))
    }

    @Test
    fun `aliases count only through the tables`() {
        assertNotNull(check(listOf(job("Ran workloads on k8s"))).check("Kubernetes", claim("Ran workloads on k8s")))
        assertNotNull(check(listOf(job("Shipped LLM features"))).check("AI", claim("Shipped LLM features")))
        assertNull(check(listOf(job("Shipped LLM features"))).check("Kafka", claim("Shipped LLM features")))
    }

    @Test
    fun `html or over-long quotes fail`() {
        val c = check(listOf(job("Built a <b>Kafka</b> pipeline " + "x".repeat(300))))
        assertNull(c.check("Kafka", claim("<b>Kafka</b>")))
        assertNull(c.check("Kafka", claim("Kafka" + " x".repeat(120))))
        assertNull(c.check("Kafka", claim("   ")))
    }

    @Test
    fun `the skill must be a whole word, so Go is not found in good`() {
        assertNull(check(listOf(job("Did a good job on services"))).check("Go", claim("did a good job")))
        assertNotNull(check(listOf(job("Wrote services in Go and Rust"))).check("Go", claim("services in Go")))
        assertNull(check(listOf(job("Wrote C++ services"))).check("C", claim("Wrote C++ services")))
        assertNotNull(check(listOf(job("Used Spring-Boot daily"))).check("Spring Boot", claim("Used Spring-Boot")))
        assertNull(check(listOf(job("Kafkaesque processes"))).check("Kafka", claim("Kafkaesque")))
    }

    @Test
    fun `technologies and title are part of the entry text`() {
        assertNotNull(check(listOf(job("Did things", tech = listOf("Terraform", "AWS")))).check("Terraform", claim("Terraform, AWS")))
    }

    @Test
    fun `an old stored row without evidence reads, and the evidence field round-trips`() {
        val json = JsonMapper.builder().addModule(kotlinModule()).build()
        val old = json.readValue("""[{"name":"Kafka","importance":"REQUIRED","related":["Kotlin"]}]""", Array<StoredAsked>::class.java).single()
        assertNull(old.evidence)
        val m = SkillMatcher.match(listOf(old), listOf("Kotlin"), evidence = check(listOf(job("Kafka"))))
        assertTrue(m.evidenced.isEmpty())
        assertEquals(listOf("Kafka"), m.gaps.map { it.name })
        val round = json.readValue(json.writeValueAsString(listOf(StoredAsked("AI", SkillImportance.REQUIRED, evidence = claim("q")))), Array<StoredAsked>::class.java)
        assertEquals(claim("q"), round.single().evidence)
    }

    @Test
    fun `listed is decided first and ignores evidence`() {
        val asked = StoredAsked("Kotlin", SkillImportance.REQUIRED, evidence = claim("Wrote Kotlin"))
        val m = SkillMatcher.match(listOf(asked), listOf("Kotlin"), evidence = check(listOf(job("Wrote Kotlin"))))
        assertEquals(listOf("Kotlin"), m.include.map { it.name })
        assertTrue(m.evidenced.isEmpty() && m.gaps.isEmpty())
    }

    @Test
    fun `the model input has the caps and none of the excluded fields`() {
        val long = "a".repeat(500)
        val experience = (0 until 20).map { i -> job(long, "short $i", tech = listOf("T$i"), title = "Title$i") }
        val projects = (0 until 15).map { project("proj$it", "d".repeat(3000)) }
        val profile = Profile(
            contact = Contact(fullName = "NAMEWORD", email = "mail@example.org", phone = "+41 79 000"),
            workAuthorisation = WorkAuthorisation.PERMIT_B, summary = "SUMMARYWORD",
            preferences = Preferences(targetRoles = listOf("PREFWORD"), minSalaryChf = 123456),
            experience = experience, openSourceProjects = projects, skills = listOf(Skill("Kotlin")),
        )
        val input = EvidenceInputBuilder.build(profile)
        assertEquals(listOf("Kotlin"), input.savedSkillNames)
        assertTrue(input.positions.size <= 12 && input.projects.size <= 10)
        assertEquals((0 until input.positions.size).toList(), input.positions.map { it.index })
        assertTrue(input.positions.flatMap { it.achievements }.all { it.length <= 300 })
        val total = input.positions.sumOf { p -> p.title.length + p.employer.length + p.achievements.sumOf { it.length } + p.technologies.sumOf { it.length } } +
            input.projects.sumOf { p -> p.name.length + (p.description?.length ?: 0) + (p.summary?.length ?: 0) }
        assertTrue(total <= 15_000, "total $total")
        val text = input.toString()
        listOf("NAMEWORD", "mail@example.org", "+41 79", "SUMMARYWORD", "PREFWORD", "123456", "PERMIT").forEach { assertTrue(it !in text, it) }
    }
}
