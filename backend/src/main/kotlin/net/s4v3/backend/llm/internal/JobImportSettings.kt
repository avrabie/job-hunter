package net.s4v3.backend.llm.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(JobImportSettings::class)
internal class JobImportConfig

/** `jobhunter.import.*` (read here: `model`, `timeout`). An empty `model` means the model of `jobhunter.llm.model`. */
@ConfigurationProperties("jobhunter.import")
internal data class JobImportSettings(
    val model: String = "",
    val timeout: Duration = Duration.ofSeconds(60),
)
