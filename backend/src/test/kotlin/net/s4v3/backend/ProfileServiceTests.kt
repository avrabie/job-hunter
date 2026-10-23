package net.s4v3.backend

import net.s4v3.backend.profile.Education
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileConflictException
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.ProfileUpdate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class ProfileServiceTests(
    @Autowired private val profiles: ProfileService,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun newUser(): String {
        val sub = "profile-user-" + UUID.randomUUID()
        jdbc.update("insert into app_user (sub) values (?)", sub)
        return sub
    }

    private fun fullBody() = javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes()

    private fun fullProfile(): Profile = profiles.parse(("""{"version":0,"profile":""" + String(fullBody()) + "}").toByteArray()).profile

    @Test
    fun `a user who never saved gets an empty profile at version 0`() {
        val stored = profiles.get(newUser())

        assertEquals(0, stored.version)
        assertEquals(Profile(), stored.profile)
        assertNull(stored.profile.location)
        assertTrue(stored.profile.experience.isEmpty())
    }

    @Test
    fun `first save gets version 1 and reads back, then an update gets version 2`() {
        val sub = newUser()
        val saved = profiles.replace(sub, ProfileUpdate(0, fullProfile()))
        assertEquals(1, saved.version)
        assertEquals(saved, profiles.get(sub))

        val changed = fullProfile().copy(summary = "Changed")
        assertEquals(2, profiles.replace(sub, ProfileUpdate(1, changed)).version)
        assertEquals("Changed", profiles.get(sub).profile.summary)
    }

    @Test
    fun `a profile using every field round-trips with entries in order`() {
        val sub = newUser()
        val original = fullProfile()
        profiles.replace(sub, ProfileUpdate(0, original))

        val back = profiles.get(sub).profile
        assertEquals(original, back)
        assertEquals(listOf("Example AG", "Beispiel GmbH"), back.experience.map { it.employer })
        assertNull(back.experience[0].end)
        assertEquals("Fast Joins on Skewed Data", back.publications.single().title)
    }

    @Test
    fun `a profile stored before publications existed reads with an empty list`() {
        val sub = newUser()
        jdbc.update(
            "insert into profile (owner_sub, document, schema_version, version) values (?, CAST(? AS jsonb), 1, 3)",
            sub,
            """{"summary":"Stored before publications"}""",
        )

        val stored = profiles.get(sub)

        assertEquals(3, stored.version)
        assertEquals("Stored before publications", stored.profile.summary)
        assertEquals(emptyList(), stored.profile.publications)
    }

    @Test
    fun `a profile stored before projects and technologies existed reads with the defaults`() {
        val sub = newUser()
        jdbc.update(
            "insert into profile (owner_sub, document, schema_version, version) values (?, CAST(? AS jsonb), 1, 4)",
            sub,
            """{"experience":[{"employer":"E","title":"T","start":"2020-01"}]}""",
        )

        val stored = profiles.get(sub)

        assertEquals(4, stored.version)
        assertEquals(emptyList(), stored.profile.projects)
        assertEquals(emptyList(), stored.profile.experience.single().technologies)
    }

    @Test
    fun `a stale version is rejected and changes nothing`() {
        val sub = newUser()
        profiles.replace(sub, ProfileUpdate(0, fullProfile()))
        profiles.replace(sub, ProfileUpdate(1, fullProfile().copy(summary = "second")))

        assertFailsWith<ProfileConflictException> { profiles.replace(sub, ProfileUpdate(1, fullProfile().copy(summary = "stale"))) }
        assertFailsWith<ProfileConflictException> { profiles.replace(sub, ProfileUpdate(0, fullProfile().copy(summary = "stale"))) }

        val stored = profiles.get(sub)
        assertEquals(2, stored.version)
        assertEquals("second", stored.profile.summary)
    }

    @Test
    fun `two saves with the same version race and exactly one wins`() {
        repeat(5) {
            val sub = newUser()
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val results = (1..2).map { n ->
                    pool.submit(Callable {
                        start.await()
                        runCatching { profiles.replace(sub, ProfileUpdate(0, Profile(summary = "writer $n"))) }
                    })
                }
                start.countDown()
                val outcomes = results.map { it.get() }

                assertEquals(1, outcomes.count { it.isSuccess }, outcomes.toString())
                assertEquals(1, outcomes.count { it.exceptionOrNull() is ProfileConflictException }, outcomes.toString())
                assertEquals(1, profiles.get(sub).version)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun `user A and user B each keep their own profile`() {
        val a = newUser()
        val b = newUser()
        profiles.replace(a, ProfileUpdate(0, Profile(summary = "A's summary")))
        profiles.replace(b, ProfileUpdate(0, Profile(summary = "B's summary")))
        profiles.replace(a, ProfileUpdate(1, Profile(summary = "A changed")))

        assertEquals("A changed", profiles.get(a).profile.summary)
        assertEquals("B's summary", profiles.get(b).profile.summary)
        assertEquals(1, profiles.get(b).version)
    }

    @Test
    fun `broken rules are all reported by path and never contain the submitted text`() {
        val marker = "SECRET-MARKER-" + "x".repeat(250)
        val profile = Profile(
            experience = listOf(
                Experience("", marker, YearMonth.of(2020, 5), YearMonth.of(2019, 1)),
            ),
            education = listOf(Education(marker, "MSc", start = YearMonth.of(2020, 1), end = YearMonth.of(2019, 1))),
        )

        val e = assertFailsWith<ProfileInvalidException> { profiles.replace(newUser(), ProfileUpdate(0, profile)) }

        assertEquals("must not be blank", e.fields["experience[0].employer"])
        assertTrue("experience[0].title" in e.fields)
        assertTrue("experience[0].end" in e.fields)
        assertTrue("education[0].institution" in e.fields)
        assertTrue("education[0].end" in e.fields)
        assertTrue(e.fields.values.none { "SECRET-MARKER" in it } && "SECRET-MARKER" !in (e.message ?: ""))
    }

    @Test
    fun `size limits are enforced and nothing is stored`() {
        val sub = newUser()
        val entry = Experience("E", "T", YearMonth.of(2020, 1))
        val tooMany = Profile(experience = List(31) { entry })
        assertTrue("experience" in assertFailsWith<ProfileInvalidException> { profiles.replace(sub, ProfileUpdate(0, tooMany)) }.fields)

        val longSummary = Profile(summary = "s".repeat(4001))
        assertTrue("summary" in assertFailsWith<ProfileInvalidException> { profiles.replace(sub, ProfileUpdate(0, longSummary)) }.fields)

        assertEquals(0, profiles.get(sub).version)
    }

    @Test
    fun `workload, salary and permit rules`() {
        val sub = newUser()
        val prefs = fullProfile().preferences
        fun fields(p: Profile) = assertFailsWith<ProfileInvalidException> { profiles.replace(sub, ProfileUpdate(0, p)) }.fields

        assertTrue("preferences.workloadMin" in fields(Profile(preferences = prefs.copy(workloadMin = 5))))
        assertTrue("preferences.workloadMax" in fields(Profile(preferences = prefs.copy(workloadMin = 90, workloadMax = 80))))
        assertTrue("preferences.minSalaryChf" in fields(Profile(preferences = prefs.copy(minSalaryChf = -1))))
    }

    @Test
    fun `parse rejects unknown fields, unknown enum values, wrong types and malformed json without echoing them`() {
        fun fields(json: String) = assertFailsWith<ProfileInvalidException> { profiles.parse(json.toByteArray()) }.fields

        assertEquals(setOf("surprise"), fields("""{"version":0,"profile":{},"surprise":"SECRET-MARKER"}""").keys)
        assertEquals(setOf("workAuthorisation"), fields("""{"version":0,"profile":{"workAuthorisation":"CITIZEN"}}""").keys)
        assertEquals(setOf("projects[0].type"), fields("""{"version":0,"profile":{"projects":[{"name":"P","type":"HOBBY"}]}}""").keys)
        assertEquals(setOf("experience[0].start"), fields("""{"version":0,"profile":{"experience":[{"employer":"E","title":"T","start":"May 2020"}]}}""").keys)
        assertEquals(setOf("version"), fields("""{"version":"SECRET-MARKER","profile":{}}""").keys)
        assertEquals(setOf("body"), fields("""{"version": SECRET-MARKER"""").keys)
        assertTrue(fields("""{"version":0,"profile":{"workAuthorisation":"SECRET-MARKER"}}""").values.none { "SECRET-MARKER" in it })
    }
}
