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

/** `jobhunter.import.previews-per-hour` and `confirms-per-day`, per user. The shared postings make confirms the tighter limit. */
@ConfigurationProperties("jobhunter.import")
internal data class ImportLimitSettings(val previewsPerHour: Int = 20, val confirmsPerDay: Int = 10)

/** At most `limit()` calls per user in any rolling [period], in memory (one backend instance). */
internal open class WindowCounter(private val period: Duration, private val limit: () -> Int) {
    private val calls = HashMap<String, ArrayDeque<Instant>>()

    /** Null when the call is allowed (and counted), else how long until the oldest call leaves the window. */
    @Synchronized
    fun tryAcquire(sub: String, now: Instant = Instant.now()): Duration? {
        val window = calls.getOrPut(sub) { ArrayDeque() }
        while (window.isNotEmpty() && window.first() <= now.minus(period)) window.removeFirst()
        if (window.size >= limit()) return Duration.between(now, window.first().plus(period))
        window.addLast(now)
        return null
    }
}

@Component
internal class PreviewLimiter(settings: ImportLimitSettings) : WindowCounter(Duration.ofHours(1), { settings.previewsPerHour })

@Component
internal class ConfirmLimiter(settings: ImportLimitSettings) : WindowCounter(Duration.ofDays(1), { settings.confirmsPerDay })
