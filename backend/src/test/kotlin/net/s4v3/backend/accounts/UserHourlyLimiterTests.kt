package net.s4v3.backend.accounts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class UserHourlyLimiterTests {

    private val start: Instant = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `allows exactly limit calls in an hour and refuses the next`() {
        val limiter = UserHourlyLimiter { 3 }

        assertTrue(limiter.tryAcquire("alice", start))
        assertTrue(limiter.tryAcquire("alice", start.plusSeconds(1)))
        assertTrue(limiter.tryAcquire("alice", start.plusSeconds(2)))
        assertFalse(limiter.tryAcquire("alice", start.plusSeconds(3)))
    }

    @Test
    fun `allowance frees up exactly one hour after the first call`() {
        val limiter = UserHourlyLimiter { 1 }

        assertTrue(limiter.tryAcquire("alice", start))
        assertFalse(limiter.tryAcquire("alice", start.plus(Duration.ofHours(1)).minusSeconds(1)))
        assertTrue(limiter.tryAcquire("alice", start.plus(Duration.ofHours(1))))
    }

    @Test
    fun `two users have separate allowances`() {
        val limiter = UserHourlyLimiter { 1 }

        assertTrue(limiter.tryAcquire("alice", start))
        assertFalse(limiter.tryAcquire("alice", start.plusSeconds(1)))
        assertTrue(limiter.tryAcquire("bob", start.plusSeconds(1)))
    }

    @Test
    fun `a changed limit takes effect on the next call`() {
        var limit = 1
        val limiter = UserHourlyLimiter { limit }

        assertTrue(limiter.tryAcquire("alice", start))
        assertFalse(limiter.tryAcquire("alice", start.plusSeconds(1)))

        limit = 2
        assertTrue(limiter.tryAcquire("alice", start.plusSeconds(2)))
        assertFalse(limiter.tryAcquire("alice", start.plusSeconds(3)))

        limit = 0
        assertFalse(limiter.tryAcquire("alice", start.plus(Duration.ofHours(2))))
    }

    @Test
    fun `limit zero refuses everything`() {
        val limiter = UserHourlyLimiter { 0 }

        assertFalse(limiter.tryAcquire("alice", start))
        assertFalse(limiter.tryAcquire("bob", start))
    }
}
