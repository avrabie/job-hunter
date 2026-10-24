package net.s4v3.backend.llm.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(LlmSettings::class)
internal class LlmConfig

/**
 * `jobhunter.llm.*`. The key comes from `ANTHROPIC_API_KEY` (see application.yaml) and is never logged. `model` is the name
 * of a Koog model constant (`Haiku_4_5`, `Sonnet_5`, `Opus_5`): Koog's Anthropic client only accepts the models it knows.
 */
@ConfigurationProperties("jobhunter.llm")
internal data class LlmSettings(
    val apiKey: String = "",
    val model: String = "Haiku_4_5",
    /** How many times a malformed answer may be sent to the cheaper repair model before the call fails. */
    val repairs: Int = 2,
    val timeout: Duration = Duration.ofSeconds(90),
) {
    override fun toString() = "LlmSettings(model=$model, repairs=$repairs, timeout=$timeout)"
}
