package net.s4v3.backend.cv.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CvSettings::class)
internal class CvConfig

/**
 * `jobhunter.cv.*`. `maxTextChars` is the most CV text that is sent to the model; a longer CV is refused, never cut.
 * `extractionsPerHour` limits the model calls per user.
 */
@ConfigurationProperties("jobhunter.cv")
internal data class CvSettings(
    val maxTextChars: Int = 120_000,
    val extractionsPerHour: Int = 20,
)
