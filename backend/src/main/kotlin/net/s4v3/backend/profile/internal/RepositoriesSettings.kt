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
    val digest: Digest = Digest(),
) {
    data class GitHub(
        val baseUrl: String = "https://api.github.com",
        val rawBaseUrl: String = "https://raw.githubusercontent.com",
        val connectTimeout: Duration = Duration.ofSeconds(5),
        val readTimeout: Duration = Duration.ofSeconds(10),
        val maxBodyBytes: Int = 2 * 1024 * 1024,
    )

    /** Caps of a repository digest. Text longer than its cap is cut, a tree deeper or longer than its cap is cut. */
    data class Digest(
        val maxTreeBodyBytes: Int = 8 * 1024 * 1024,
        val maxTreePaths: Int = 400,
        val maxTreeDepth: Int = 3,
        val maxReadmeBytes: Int = 20 * 1024,
        val maxManifests: Int = 8,
        val maxManifestBytes: Int = 12 * 1024,
    )
}
