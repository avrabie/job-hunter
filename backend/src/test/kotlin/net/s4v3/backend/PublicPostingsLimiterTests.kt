package net.s4v3.backend

import net.s4v3.backend.postings.internal.PublicPostingsLimiter
import net.s4v3.backend.postings.internal.PublicPostingsSettings
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicPostingsLimiterTests {

    private class TestClock(var now: Instant = Instant.parse("2026-01-01T10:00:00Z")) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = now
    }

    private val clock = TestClock()

    private fun limiter(perMinute: Int = 60, maxClients: Int = 10_000) =
        PublicPostingsLimiter(PublicPostingsSettings(perMinute), clock, maxClients)

    @Test
    fun `the 61st request in a minute is refused`() {
        val limiter = limiter()

        repeat(60) { assertTrue(limiter.tryAcquire("a"), "request ${it + 1}") }

        assertFalse(limiter.tryAcquire("a"))
    }

    @Test
    fun `another client is not affected`() {
        val limiter = limiter(perMinute = 1)

        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"))
    }

    @Test
    fun `the allowance returns after a minute and Retry-After counts down to it`() {
        val limiter = limiter(perMinute = 1)
        assertTrue(limiter.tryAcquire("a"))

        clock.now = clock.now.plusSeconds(20)
        assertFalse(limiter.tryAcquire("a"))
        assertEquals(40, limiter.retryAfterSeconds("a"))

        clock.now = clock.now.plusSeconds(39)
        assertFalse(limiter.tryAcquire("a"))
        clock.now = clock.now.plusSeconds(1)
        assertTrue(limiter.tryAcquire("a"))
    }

    @Test
    fun `Retry-After is at least one second`() {
        val limiter = limiter(perMinute = 1)
        limiter.tryAcquire("a")
        clock.now = clock.now.plus(Duration.ofMillis(59_900))

        assertEquals(1, limiter.retryAfterSeconds("a"))
        assertEquals(1, limiter.retryAfterSeconds("never-seen"))
    }

    @Test
    fun `state stays bounded under many distinct addresses and a new client is still served`() {
        val limiter = limiter(maxClients = 100)

        repeat(10_000) { assertTrue(limiter.tryAcquire("10.0.${it / 250}.${it % 250}")) }

        assertEquals(100, limiter.size())
        assertTrue(limiter.tryAcquire("a-new-client"))
        assertEquals(100, limiter.size())
    }

    @Test
    fun `the least recently used address is the one dropped`() {
        val limiter = limiter(perMinute = 1, maxClients = 2)
        limiter.tryAcquire("a")
        limiter.tryAcquire("b")
        limiter.tryAcquire("a") // refused, but a is now the most recent
        limiter.tryAcquire("c") // drops b

        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"))
    }
}
