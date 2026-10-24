package net.s4v3.backend.cv.publiccv.internal

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A sliding-window limiter per key: at most [limit] calls for one key in any [window]. In memory (one backend instance).
 * Keys are never logged. A sweep runs when many keys have piled up, so a client that invents keys cannot grow the map
 * without bound.
 */
internal class WindowLimiter(
    private val limit: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val calls = HashMap<String, ArrayDeque<Instant>>()

    /** True and counted when the call is allowed; false and not counted otherwise. */
    @Synchronized
    fun tryAcquire(key: String): Boolean {
        val now = clock.instant()
        if (calls.size > SWEEP_AT) sweep(now)
        val queue = calls.getOrPut(key) { ArrayDeque() }
        evict(queue, now)
        if (queue.size >= limit) return false
        queue.addLast(now)
        return true
    }

    /** How long until the key may call again; zero when it may now. For `Retry-After`. */
    @Synchronized
    fun retryAfter(key: String): Duration {
        val now = clock.instant()
        val queue = calls[key] ?: return Duration.ZERO
        evict(queue, now)
        if (queue.size < limit) return Duration.ZERO
        return Duration.between(now, queue.first().plus(window))
    }

    private fun evict(queue: ArrayDeque<Instant>, now: Instant) {
        // A call exactly [window] old no longer counts: the window is (now - window, now].
        while (queue.isNotEmpty() && !queue.first().isAfter(now.minus(window))) queue.removeFirst()
    }

    private fun sweep(now: Instant) {
        calls.values.forEach { evict(it, now) }
        calls.values.removeIf { it.isEmpty() }
    }

    private companion object {
        const val SWEEP_AT = 10_000
    }
}
