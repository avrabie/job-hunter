package net.s4v3.backend

import net.s4v3.backend.sources.internal.ScheduleRules
import net.s4v3.backend.sources.internal.SourcesSettings
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleRulesTests {

    private val gap = Duration.ofMinutes(15)

    @Test
    fun `the defaults are off, hourly and Zurich time`() {
        val s = SourcesSettings.Schedule()

        assertEquals(false, s.enabled)
        assertEquals("0 0 * * * *", s.cron)
        assertEquals("Europe/Zurich", s.zone)
    }

    @Test
    fun `the default cron fires at the start of every hour`() {
        val s = SourcesSettings.Schedule()
        val start = Instant.parse("2026-10-22T10:20:00Z")

        val first = ScheduleRules.nextFire(s.cron, s.zone, start)!!
        val second = ScheduleRules.nextFire(s.cron, s.zone, first)!!
        val third = ScheduleRules.nextFire(s.cron, s.zone, second)!!

        assertEquals(listOf("2026-10-22T11:00:00Z", "2026-10-22T12:00:00Z", "2026-10-22T13:00:00Z"), listOf(first, second, third).map { it.toString() })
    }

    @Test
    fun `fire times follow the zone`() {
        // 06:30 in Zurich on 22 October 2026 (UTC+2) is 04:30 UTC.
        val next = ScheduleRules.nextFire("0 30 6,14 * * *", "Europe/Zurich", Instant.parse("2026-10-22T00:00:00Z"))

        assertEquals(Instant.parse("2026-10-22T04:30:00Z"), next)
    }

    @Test
    fun `valid schedules pass`() {
        assertNull(ScheduleRules.validate("0 0 * * * *", "Europe/Zurich", gap))
        assertNull(ScheduleRules.validate("0 30 6,14 * * *", "Europe/Zurich", gap))
    }

    @Test
    fun `a bad cron names the cron field`() {
        assertEquals("cron", ScheduleRules.validate("not a cron", "Europe/Zurich", gap)!!.field)
    }

    @Test
    fun `a bad zone names the zone field`() {
        assertEquals("zone", ScheduleRules.validate("0 0 * * * *", "Mars/Base", gap)!!.field)
    }

    @Test
    fun `a cron faster than the minimum gap is refused`() {
        val error = ScheduleRules.validate("0 * * * * *", "Europe/Zurich", gap)!!

        assertEquals("cron", error.field)
        assertEquals(true, "15 minutes" in error.message)
    }

    @Test
    fun `missing fields are named`() {
        assertEquals("cron", ScheduleRules.validate(null, "Europe/Zurich", gap)!!.field)
        assertEquals("cron", ScheduleRules.validate(" ", "Europe/Zurich", gap)!!.field)
        assertEquals("zone", ScheduleRules.validate("0 0 * * * *", null, gap)!!.field)
    }

    @Test
    fun `error messages never echo the submitted value`() {
        val error = ScheduleRules.validate("SECRET-VALUE", "Europe/Zurich", gap)!!

        assertEquals(false, "SECRET" in error.message)
    }
}
