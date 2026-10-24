package net.s4v3.backend.llm.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(LettersSettings::class)
internal class LettersConfig

/** `jobhunter.letters.*` (read here: `model`, `timeout`). An empty `model` means the model of `jobhunter.llm.model`. */
@ConfigurationProperties("jobhunter.letters")
internal data class LettersSettings(
    val model: String = "",
    val timeout: Duration = Duration.ofSeconds(60),
)
