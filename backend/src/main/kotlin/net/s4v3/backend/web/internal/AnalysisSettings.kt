package net.s4v3.backend.web.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AnalysisSettings::class)
internal class AnalysisConfig

/** `jobhunter.analysis.*`. `perHour` limits the project analyses per user; each one is one model call. */
@ConfigurationProperties("jobhunter.analysis")
internal data class AnalysisSettings(
    val perHour: Int = 10,
)
