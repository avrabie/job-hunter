package net.s4v3.backend

import net.s4v3.backend.sources.internal.ScheduleStore
import net.s4v3.backend.sources.internal.SourcesSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
internal class ScheduleStoreTests(
    @Autowired private val store: ScheduleStore,
    @Autowired private val jdbcClient: JdbcClient,
    @Autowired private val jdbc: JdbcTemplate,
) {

    @BeforeEach
    @AfterEach
    fun clean() {
        jdbc.update("delete from crawl_schedule")
    }

    @Test
    fun `without a row the settings apply`() {
        val s = store.current()

        assertEquals(false, s.enabled)
        assertEquals("0 0 * * * *", s.cron)
        assertEquals("Europe/Zurich", s.zone)
        assertNull(s.updatedAt)
    }

    @Test
    fun `a stored row wins over the settings, also for a new store instance`() {
        store.save(true, "0 30 6,14 * * *", "Europe/Zurich", "sub-1")

        val other = ScheduleStore(jdbcClient, SourcesSettings(schedule = SourcesSettings.Schedule(cron = "0 5 * * * *")))
        val s = other.current()

        assertEquals(true, s.enabled)
        assertEquals("0 30 6,14 * * *", s.cron)
        assertEquals("sub-1", s.updatedBy)
        assertNotNull(s.updatedAt)
    }

    @Test
    fun `saving twice leaves one row with the latest values`() {
        store.save(true, "0 0 * * * *", "Europe/Zurich", "a")
        store.save(false, "0 0 12 * * *", "UTC", "b")

        assertEquals(1, jdbc.queryForObject("select count(*) from crawl_schedule", Int::class.java))
        val s = store.current()
        assertEquals(false, s.enabled)
        assertEquals("UTC", s.zone)
        assertEquals("b", s.updatedBy)
    }

    @Test
    fun `enabled initial settings with a bad cron stop startup naming the setting`() {
        val bad = ScheduleStore(jdbcClient, SourcesSettings(schedule = SourcesSettings.Schedule(enabled = true, cron = "not a cron")))
        val badZone = ScheduleStore(jdbcClient, SourcesSettings(schedule = SourcesSettings.Schedule(enabled = true, zone = "Mars/Base")))

        assertTrue("jobhunter.sources.schedule.cron" in assertFailsWith<IllegalStateException> { bad.afterSingletonsInstantiated() }.message!!)
        assertTrue("jobhunter.sources.schedule.zone" in assertFailsWith<IllegalStateException> { badZone.afterSingletonsInstantiated() }.message!!)
    }

    @Test
    fun `a disabled schedule or a stored one is not validated`() {
        ScheduleStore(jdbcClient, SourcesSettings(schedule = SourcesSettings.Schedule(cron = "not a cron"))).afterSingletonsInstantiated()

        store.save(true, "0 0 * * * *", "UTC", "a")
        ScheduleStore(jdbcClient, SourcesSettings(schedule = SourcesSettings.Schedule(enabled = true, cron = "not a cron")))
            .afterSingletonsInstantiated()
    }
}
