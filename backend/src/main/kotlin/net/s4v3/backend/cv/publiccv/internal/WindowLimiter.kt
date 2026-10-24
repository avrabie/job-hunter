package net.s4v3.backend.cv.publiccv.internal

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A sliding-window limiter per key: at most [limit] calls for one key in any [window]. In memory (one backend instance).
 * Keys are never logged. At most once per window a sweep drops the idle keys; the map never holds more than [maxKeys], and when it is
 * full a new key is refused, so a client that invents keys can neither grow it nor make calls slower.
 */
internal class WindowLimiter(
    private val limit: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
    private val maxKeys: Int = 20_000,
) {

    private val calls = HashMap<String, ArrayDeque<Instant>>()
    private var lastSweep: Instant = clock.instant()

    /** True and counted when the call is allowed; false and not counted otherwise. */
    @Synchronized
    fun tryAcquire(key: String): Boolean {
        val now = clock.instant()
        if (!now.isBefore(lastSweep.plus(window))) sweep(now)
        // A full map takes no new key (idle keys went in the sweep), so invented keys cannot grow it or make calls slower.
        val queue = calls[key] ?: if (calls.size >= maxKeys) return false else ArrayDeque<Instant>().also { calls[key] = it }
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

    @Synchronized
    fun size(): Int = calls.size

    private fun evict(queue: ArrayDeque<Instant>, now: Instant) {
        // A call exactly [window] old no longer counts: the window is (now - window, now].
        while (queue.isNotEmpty() && !queue.first().isAfter(now.minus(window))) queue.removeFirst()
    }

    private fun sweep(now: Instant) {
        lastSweep = now
        calls.values.forEach { evict(it, now) }
        calls.values.removeIf { it.isEmpty() }
    }
}
