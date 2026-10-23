package net.s4v3.backend.cv.internal

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/** At most `limit` model calls per user in any rolling hour. Kept in memory: there is one backend instance. */
@Component
internal class ExtractionLimiter(private val settings: CvSettings) {

    private val calls = HashMap<String, ArrayDeque<Instant>>()

    @Synchronized
    fun tryAcquire(sub: String, now: Instant = Instant.now()): Boolean {
        val window = calls.getOrPut(sub) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= now.minus(PERIOD)) window.removeFirst()
        if (window.size >= settings.extractionsPerHour) return false
        window.addLast(now)
        return true
    }

    private companion object {
        val PERIOD: Duration = Duration.ofHours(1)
    }
}
