package net.s4v3.backend.web.internal

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/** At most `perHour` project analyses per user in any rolling hour. Kept in memory: there is one backend instance. */
@Component
internal class AnalysisLimiter(private val settings: AnalysisSettings) {

    private val calls = HashMap<String, ArrayDeque<Instant>>()

    @Synchronized
    fun tryAcquire(sub: String, now: Instant = Instant.now()): Boolean {
        val window = calls.getOrPut(sub) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= now.minus(PERIOD)) window.removeFirst()
        if (window.size >= settings.perHour) return false
        window.addLast(now)
        return true
    }

    private companion object {
        val PERIOD: Duration = Duration.ofHours(1)
    }
}
