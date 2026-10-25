package net.s4v3.backend.jobimport.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Configuration
@EnableConfigurationProperties(ImportLimitSettings::class)
internal class ImportLimitConfig

/**
 * `jobhunter.import.previews-per-hour` and `confirms-per-day` per user; `max-concurrent-previews-per-user` and
 * `max-concurrent-previews` cap the fetch-and-extract work that runs at the same moment.
 */
@ConfigurationProperties("jobhunter.import")
internal data class ImportLimitSettings(
    val previewsPerHour: Int = 20,
    val confirmsPerDay: Int = 10,
    val maxConcurrentPreviewsPerUser: Int = 2,
    val maxConcurrentPreviews: Int = 8,
)

/** Previews in flight, per user and in total. [tryEnter] must be paired with [leave] in a finally block. */
@Component
internal class InFlightLimiter(private val settings: ImportLimitSettings) {
    private val perUser = HashMap<String, Int>()
    private var total = 0

    /** False (nothing counted) when the user or the whole server is at its cap. */
    @Synchronized
    fun tryEnter(sub: String): Boolean {
        val mine = perUser[sub] ?: 0
        if (mine >= settings.maxConcurrentPreviewsPerUser || total >= settings.maxConcurrentPreviews) return false
        perUser[sub] = mine + 1
        total++
        return true
    }

    @Synchronized
    fun leave(sub: String) {
        val mine = perUser[sub] ?: return
        if (mine <= 1) perUser.remove(sub) else perUser[sub] = mine - 1
        total--
    }
}

/** At most `limit()` calls per user in any rolling [period], in memory (one backend instance). */
internal open class WindowCounter(private val period: Duration, private val limit: () -> Int) {
    private val calls = HashMap<String, ArrayDeque<Instant>>()

    /** Null when the call is allowed (and counted), else how long until the oldest call leaves the window. */
    @Synchronized
    fun tryAcquire(sub: String, now: Instant = Instant.now()): Duration? {
        val window = calls.getOrPut(sub) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= now.minus(period)) window.removeFirst()
        if (window.size >= limit()) return window.firstOrNull()?.let { Duration.between(now, it.plus(period)) } ?: period
        window.addLast(now)
        return null
    }
}

@Component
internal class PreviewLimiter(settings: ImportLimitSettings) : WindowCounter(Duration.ofHours(1), { settings.previewsPerHour })

@Component
internal class ConfirmLimiter(settings: ImportLimitSettings) : WindowCounter(Duration.ofDays(1), { settings.confirmsPerDay })
