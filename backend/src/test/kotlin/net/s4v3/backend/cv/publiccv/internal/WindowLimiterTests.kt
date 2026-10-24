package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.TestClock
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class WindowLimiterTests {

    private val clock = TestClock()
    private val limiter = WindowLimiter(3, Duration.ofMinutes(1), clock)

    @Test
    fun `calls up to the limit pass and the next one is refused`() {
        repeat(3) { assertTrue(limiter.tryAcquire("a")) }
        assertFalse(limiter.tryAcquire("a"))
    }

    @Test
    fun `a refused call is not counted`() {
        repeat(3) { limiter.tryAcquire("a") }
        repeat(5) { assertFalse(limiter.tryAcquire("a")) }
        clock.advance(Duration.ofMinutes(1))
        assertTrue(limiter.tryAcquire("a"))
    }

    @Test
    fun `a call one second short of the window still counts and one exactly at the window does not`() {
        repeat(3) { limiter.tryAcquire("a") }
        clock.advance(Duration.ofSeconds(59))
        assertFalse(limiter.tryAcquire("a"))
        clock.advance(Duration.ofSeconds(1))
        assertTrue(limiter.tryAcquire("a"))
    }

    @Test
    fun `the window slides call by call`() {
        assertTrue(limiter.tryAcquire("a"))
        clock.advance(Duration.ofSeconds(30))
        assertTrue(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
        clock.advance(Duration.ofSeconds(30))
        // The first call has left the window, the other two have not.
        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
    }

    @Test
    fun `keys are separate`() {
        repeat(3) { limiter.tryAcquire("a") }
        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b"))
    }

    @Test
    fun `retry after counts down to the oldest call leaving the window`() {
        assertEquals(Duration.ZERO, limiter.retryAfter("a"))
        repeat(3) { limiter.tryAcquire("a") }
        clock.advance(Duration.ofSeconds(20))
        assertEquals(Duration.ofSeconds(40), limiter.retryAfter("a"))
        clock.advance(Duration.ofSeconds(40))
        assertEquals(Duration.ZERO, limiter.retryAfter("a"))
    }

    @Test
    fun `many invented keys do not make the limiter forget real ones`() {
        repeat(3) { limiter.tryAcquire("real") }
        repeat(10_050) { limiter.tryAcquire("k$it") }
        assertFalse(limiter.tryAcquire("real"))
    }
}
