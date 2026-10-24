package net.s4v3.backend.cv.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(ExportSettings::class)
internal class ExportConfig

/** `jobhunter.cv-export.*`. `perHour` limits the CV exports per user in any rolling hour. `renderWait` is how long a third parallel PDF render waits before 503 busy. */
@ConfigurationProperties("jobhunter.cv-export")
internal data class ExportSettings(
    val perHour: Int = 30,
    val renderWait: Duration = Duration.ofSeconds(5),
)
