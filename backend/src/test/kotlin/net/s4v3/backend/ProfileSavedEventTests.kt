package net.s4v3.backend

import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileConflictException
import net.s4v3.backend.profile.ProfileDirectory
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileSaved
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.ProfileUpdate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

open class SavedEvents {
    open val received = CopyOnWriteArrayList<ProfileSaved>()

    @EventListener
    open fun on(event: ProfileSaved) {
        received += event
    }
}

@TestConfiguration(proxyBeanMethods = false)
class SavedEventsConfig {
    @Bean
    fun savedEvents() = SavedEvents()
}

@IntegrationTest
@Import(SavedEventsConfig::class)
class ProfileSavedEventTests(
    @Autowired private val profiles: ProfileService,
    @Autowired private val directory: ProfileDirectory,
    @Autowired private val events: SavedEvents,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun newUser(prefix: String = "saved-user"): String {
        val sub = prefix + "-" + UUID.randomUUID()
        jdbc.update("insert into app_user (sub) values (?)", sub)
        return sub
    }

    private fun profile(): Profile {
        val body = javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes().decodeToString()
        return profiles.parse("""{"version":0,"profile":$body}""".toByteArray()).profile
    }

    private fun forUser(sub: String) = events.received.filter { it.userId == sub }

    @Test
    fun `a valid save publishes one event with the user and the new version`() {
        val sub = newUser()

        profiles.replace(sub, ProfileUpdate(0, profile()))
        profiles.replace(sub, ProfileUpdate(1, profile()))

        assertEquals(listOf(1L, 2L), forUser(sub).map { it.version })
    }

    @Test
    fun `a rejected save publishes nothing`() {
        val sub = newUser()
        profiles.replace(sub, ProfileUpdate(0, profile()))
        val before = forUser(sub).size

        assertFailsWith<ProfileConflictException> { profiles.replace(sub, ProfileUpdate(0, profile())) }
        assertFailsWith<ProfileInvalidException> {
            profiles.replace(sub, ProfileUpdate(1, profile().copy(summary = "x".repeat(100_000))))
        }

        assertEquals(before, forUser(sub).size)
    }

    @Test
    fun `the event holds only the user id and the version`() {
        assertEquals(setOf("userId", "version"), ProfileSaved::class.java.declaredFields.map { it.name }.toSet())
    }

    @Test
    fun `the directory lists exactly the users who saved, in order, across pages`() {
        val prefix = "dir-" + UUID.randomUUID()
        val saved = (1..3).map { newUser(prefix) }.sorted()
        newUser(prefix) // never saves
        saved.forEach { profiles.replace(it, ProfileUpdate(0, profile())) }

        val all = mutableListOf<String>()
        var after: String? = null
        do {
            val page = directory.page(after, 2)
            all += page.map { it.sub }
            after = page.lastOrNull()?.sub
        } while (page.isNotEmpty())

        assertEquals(saved, all.filter { it.startsWith(prefix) })
        assertEquals(all.sorted(), all)
        assertTrue(directory.page(null, 1000).size <= ProfileDirectory.MAX_PAGE_SIZE)
    }
}
