package net.s4v3.backend.accounts

import java.time.Duration
import java.time.Instant

/**
 * Limits calls per user per rolling hour: at most `limit()` calls for one `sub` in any one-hour window.
 * `limit` is read on every call, so a changed value applies at once. Kept in memory: there is one backend instance.
 */
open class UserHourlyLimiter(private val limit: () -> Int) {

    private val calls = HashMap<String, ArrayDeque<Instant>>()

    @Synchronized
    fun tryAcquire(sub: String, now: Instant = Instant.now()): Boolean {
        val window = calls.getOrPut(sub) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= now.minus(PERIOD)) window.removeFirst()
        if (window.size >= limit()) return false
        window.addLast(now)
        return true
    }

    private companion object {
        val PERIOD: Duration = Duration.ofHours(1)
    }
}
