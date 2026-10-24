package net.s4v3.backend.profile.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(RepositoriesSettings::class)
internal class RepositoriesConfig

/** Settings under `jobhunter.repositories`. The defaults are what production runs with. */
@ConfigurationProperties("jobhunter.repositories")
internal data class RepositoriesSettings(
    val userAgent: String = "JobHunter/0.1 (+https://s4v3.net)",
    val github: GitHub = GitHub(),
) {
    data class GitHub(
        val baseUrl: String = "https://api.github.com",
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val readTimeout: Duration = Duration.ofSeconds(10),
        val maxBodyBytes: Int = 2 * 1024 * 1024,
    )
}
