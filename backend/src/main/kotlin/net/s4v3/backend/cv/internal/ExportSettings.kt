package net.s4v3.backend.cv.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ExportSettings::class)
internal class ExportConfig

/** `jobhunter.cv-export.*`. `perHour` limits the CV exports per user in any rolling hour. */
@ConfigurationProperties("jobhunter.cv-export")
internal data class ExportSettings(
    val perHour: Int = 30,
)
