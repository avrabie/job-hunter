package net.s4v3.backend

import net.s4v3.backend.matching.internal.Prefilter
import net.s4v3.backend.postings.Posting
import net.s4v3.backend.profile.LanguageLevel
import net.s4v3.backend.profile.LanguageSkill
import net.s4v3.backend.profile.Preferences
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.Skill
import net.s4v3.backend.profile.WorkAuthorisation
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.s4v3.backend.postings.RemoteMode as PostingRemote
import net.s4v3.backend.profile.RemoteMode as ProfileRemote

internal class PrefilterTests {

    private fun posting(
        title: String = "Backend Engineer", description: String = "We build things.", canton: String? = "ZH",
        language: String = "en", outOfScope: Boolean = false, workloadMin: Int? = null, workloadMax: Int? = null,
        remote: PostingRemote? = null, salaryMax: Int? = null,
    ) = Posting(
        UUID.randomUUID(), title, "Acme", description, "https://example.org/1", "Zurich", canton, workloadMin, workloadMax,
        remote, null, salaryMax, language, outOfScope, false, Instant.now(), Instant.now(),
    )

    private fun profile(prefs: Preferences = Preferences(), skills: List<String> = emptyList(), languages: List<LanguageSkill> = emptyList()) =
        Profile(preferences = prefs, skills = skills.map { Skill(it) }, languages = languages)

    private val roles = Preferences(targetRoles = listOf("Backend Engineer"))

    private fun eval(p: Profile, post: Posting, threshold: Int = 30) = Prefilter.evaluate(p, post, threshold)

    @Test
    fun `a canton outside the preferred ones fails the posting`() {
        val r = eval(profile(Preferences(targetRoles = listOf("Backend Engineer"), cantons = listOf("ZH", "ZG"))), posting(canton = "GE"))

        assertFalse(r.passed)
        assertTrue("CANTON_MISMATCH" in r.reasons)
    }

    @Test
    fun `an unknown canton does not fail the rule and is recorded`() {
        val r = eval(profile(Preferences(targetRoles = listOf("Backend Engineer"), cantons = listOf("ZH"))), posting(canton = null))

        assertTrue(r.passed)
        assertTrue("UNKNOWN_CANTON" in r.reasons && "CANTON_MISMATCH" !in r.reasons)
    }

    @Test
    fun `without a canton preference no canton reason is recorded`() {
        val r = eval(profile(roles), posting(canton = "GE"))

        assertTrue(r.reasons.none { it.contains("CANTON") })
    }

    @Test
    fun `workload ranges that touch pass and ranges that do not overlap fail`() {
        val user = profile(Preferences(targetRoles = listOf("Backend Engineer"), workloadMin = 60, workloadMax = 80))

        assertTrue("WORKLOAD_MISMATCH" !in eval(user, posting(workloadMin = 80, workloadMax = 100)).reasons)
        val miss = eval(user, posting(workloadMin = 100, workloadMax = 100))
        assertFalse(miss.passed)
        assertTrue("WORKLOAD_MISMATCH" in miss.reasons)
        assertTrue("UNKNOWN_WORKLOAD" in eval(user, posting()).reasons)
    }

    @Test
    fun `a remote mode the user does not accept fails`() {
        val user = profile(Preferences(targetRoles = listOf("Backend Engineer"), remoteModes = listOf(ProfileRemote.REMOTE)))

        val r = eval(user, posting(remote = PostingRemote.ONSITE))

        assertFalse(r.passed)
        assertTrue("REMOTE_MISMATCH" in r.reasons)
        assertTrue(eval(user, posting(remote = PostingRemote.REMOTE)).passed)
        assertTrue("UNKNOWN_REMOTE" in eval(user, posting()).reasons)
    }

    @Test
    fun `a top salary below the minimum fails`() {
        val user = profile(Preferences(targetRoles = listOf("Backend Engineer"), minSalaryChf = 120_000))

        val r = eval(user, posting(salaryMax = 100_000))

        assertFalse(r.passed)
        assertTrue("SALARY_BELOW_MINIMUM" in r.reasons)
        assertTrue("UNKNOWN_SALARY" in eval(user, posting()).reasons)
    }

    @Test
    fun `an out of scope posting never passes`() {
        val r = eval(profile(roles), posting(outOfScope = true))

        assertFalse(r.passed)
        assertTrue("OUT_OF_SCOPE" in r.reasons)
    }

    @Test
    fun `work authorisation is not used`() {
        val user = profile(roles).copy(workAuthorisation = WorkAuthorisation.NEEDS_SPONSORSHIP)

        assertTrue(eval(user, posting()).passed)
    }

    @Test
    fun `title, five skills and a spoken language give 100`() {
        val user = profile(roles, listOf("Kotlin", "PostgreSQL", "Kubernetes", "Docker", "Spring"), listOf(LanguageSkill("de", LanguageLevel.C1)))
        val post = posting(
            title = "Senior Backend Engineer", language = "de",
            description = "Wir nutzen Kotlin, PostgreSQL, Kubernetes, Docker und Spring.",
        )

        val r = eval(user, post)

        assertEquals(100, r.score)
        assertTrue(r.passed)
        assertEquals(listOf("ROLE_MATCH", "SKILL_MATCH_5", "LANGUAGE_MATCH"), r.reasons)
    }

    @Test
    fun `partial credit for words and skills`() {
        val user = profile(Preferences(targetRoles = listOf("Senior Backend Platform Engineer")), listOf("Kotlin", "Docker", "Spring", "Rust"))

        val r = eval(user, posting(title = "Backend Engineer", description = "Kotlin, Docker and Spring."))

        // 2 of 4 role words = 25, 3 skills of 5 = 24
        assertEquals(49, r.score)
    }

    @Test
    fun `no overlap stays below the threshold`() {
        val r = eval(profile(roles, listOf("Kotlin")), posting(title = "Graphic Designer", description = "Pixels."))

        assertFalse(r.passed)
        assertTrue("BELOW_THRESHOLD" in r.reasons)
    }

    @Test
    fun `skills match whole words only`() {
        val r = eval(profile(roles, listOf("Go")), posting(title = "Designer", description = "A good place, going places."))

        assertTrue(r.reasons.none { it.startsWith("SKILL_MATCH") })
        assertTrue(eval(profile(roles, listOf("Go")), posting(description = "We write Go services.")).reasons.contains("SKILL_MATCH_1"))
    }

    @Test
    fun `skills with symbols match literally`() {
        val user = profile(roles, listOf("C++", "C#", ".NET"))

        val r = eval(user, posting(description = "We use C++ and C# on .NET."))

        assertTrue("SKILL_MATCH_3" in r.reasons, r.reasons.toString())
        assertFalse("SKILL_MATCH_1" in eval(user, posting(description = "We use C and NET.")).reasons)
    }

    @Test
    fun `past job titles stand in when there are no target roles`() {
        val user = Profile(experience = listOf(net.s4v3.backend.profile.Experience("Old AG", "Backend Engineer", java.time.YearMonth.of(2020, 1))))

        assertTrue("ROLE_MATCH" in eval(user, posting()).reasons)
    }

    @Test
    fun `a raised threshold rejects what passed before`() {
        val user = profile(Preferences(targetRoles = listOf("Senior Backend Platform Engineer")), listOf("Kotlin", "Docker", "Spring"))
        val post = posting(title = "Backend Engineer", description = "Kotlin, Docker and Spring.")

        assertTrue(eval(user, post, threshold = 30).passed)
        assertFalse(eval(user, post, threshold = 60).passed)
    }

    @Test
    fun `reasons are fixed codes and never hold profile or posting text`() {
        val user = profile(Preferences(targetRoles = listOf("Secretrole"), cantons = listOf("GE")), listOf("Secretskill"))

        val r = eval(user, posting(title = "Secretrole at Secretco", description = "Secretskill"))

        assertTrue(r.reasons.all { it.matches(Regex("[A-Z_]+[0-9]*")) }, r.reasons.toString())
    }
}
