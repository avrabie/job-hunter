package net.s4v3.backend.jobimport.internal

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportLimiterTests {

    @Test
    fun `a window with limit 0 denies every call without throwing`() {
        val counter = WindowCounter(Duration.ofHours(1)) { 0 }
        val now = Instant.parse("2026-01-01T00:00:00Z")

        assertNotNull(counter.tryAcquire("a", now))
        assertNotNull(counter.tryAcquire("a", now.plusSeconds(5)))
    }

    @Test
    fun `a window allows its limit and frees a slot when the oldest call leaves`() {
        val counter = WindowCounter(Duration.ofHours(1)) { 2 }
        val now = Instant.parse("2026-01-01T00:00:00Z")

        assertNull(counter.tryAcquire("a", now))
        assertNull(counter.tryAcquire("a", now.plusSeconds(10)))
        assertEquals(Duration.ofSeconds(3590), counter.tryAcquire("a", now.plusSeconds(10)))
        assertNull(counter.tryAcquire("a", now.plus(Duration.ofHours(1))))
        assertNull(counter.tryAcquire("b", now))
    }

    @Test
    fun `in-flight caps count per user and in total, and leave frees the slot`() {
        val limiter = InFlightLimiter(ImportLimitSettings(maxConcurrentPreviewsPerUser = 2, maxConcurrentPreviews = 3))

        assertTrue(limiter.tryEnter("a"))
        assertTrue(limiter.tryEnter("a"))
        assertFalse(limiter.tryEnter("a"), "per-user cap")
        assertTrue(limiter.tryEnter("b"))
        assertFalse(limiter.tryEnter("c"), "global cap")
        limiter.leave("a")
        assertTrue(limiter.tryEnter("c"))
        limiter.leave("zzz") // never entered: ignored
        assertFalse(limiter.tryEnter("c"))
    }
}
