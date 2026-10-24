package net.s4v3.backend.cv.publiccv.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Configuration
@EnableConfigurationProperties(PublicCvLimitSettings::class)
internal class PublicCvConfig {
    @Bean
    fun renderCache(): RenderCache = RenderCache()
}

/**
 * `jobhunter.public-cv.*`: the limits of the public page (per client IP and minute, per handle and minute) and of the
 * owner endpoints (per user and hour: handle checks, previews, saves and removals).
 */
@ConfigurationProperties("jobhunter.public-cv")
internal data class PublicCvLimitSettings(
    val pagePerIpPerMinute: Int = 60,
    val notFoundPerIpPerMinute: Int = 10,
    val pagePerHandlePerMinute: Int = 600,
    val handleChecksPerHour: Int = 60,
    val previewsPerHour: Int = 30,
    val savesPerHour: Int = 30,
)

/** The limiters of design decision 9, built from [PublicCvLimitSettings]. The controllers (group 4) use them; keys are never logged. */
@Component
internal class PublicCvLimits(settings: PublicCvLimitSettings, clock: Clock = Clock.systemUTC()) {
    val pageByIp = WindowLimiter(settings.pagePerIpPerMinute, Duration.ofMinutes(1), clock)
    val notFoundByIp = WindowLimiter(settings.notFoundPerIpPerMinute, Duration.ofMinutes(1), clock)
    val pageByHandle = WindowLimiter(settings.pagePerHandlePerMinute, Duration.ofMinutes(1), clock)
    val handleChecks = WindowLimiter(settings.handleChecksPerHour, Duration.ofHours(1), clock)
    val previews = WindowLimiter(settings.previewsPerHour, Duration.ofHours(1), clock)
    val saves = WindowLimiter(settings.savesPerHour, Duration.ofHours(1), clock)
}
