package net.s4v3.backend.sources.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableConfigurationProperties(SourcesSettings::class)
internal class SourcesConfig

/** Settings under `jobhunter.sources`. The defaults are what production runs with. */
@ConfigurationProperties("jobhunter.sources")
internal data class SourcesSettings(
    val userAgent: String = "JobHunter/0.1 (+https://s4v3.net)",
    val pause: Duration = Duration.ofSeconds(1),
    val minInterval: Duration = Duration.ofMinutes(15),
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(30),
    val maxListBytes: Int = 8 * 1024 * 1024,
    val maxJobBytes: Int = 1024 * 1024,
    val greenhouse: Greenhouse = Greenhouse(),
    val swissdevjobs: SwissDevJobs = SwissDevJobs(),
    val schedule: Schedule = Schedule(),
) {
    /** The initial crawl schedule; a schedule stored through the API replaces it. */
    data class Schedule(
        val enabled: Boolean = false,
        val cron: String = "0 0 * * * *",
        val zone: String = "Europe/Zurich",
    )

    /** The SwissDevJobs board: its JSON job list and RSS feed under [baseUrl]. */
    data class SwissDevJobs(
        val enabled: Boolean = true,
        val baseUrl: String = "https://swissdevjobs.ch",
    )

    data class Greenhouse(
        val baseUrl: String = "https://boards-api.greenhouse.io",
        val boards: List<String> = listOf(
            "proton", "getyourguide", "scandit", "databricks", "elastic", "mongodb", "gitlab", "datadog",
            "grafanalabs", "ripple",
        ),
        val itKeywords: List<String> = listOf(
            "engineer", "engineering", "developer", "software", "devops", "sre", "reliability", "architect",
            "scientist", "machine learning", "security", "administrator", "programmer", "backend", "frontend",
            "fullstack",
        ),
        val excludedKeywords: List<String> = listOf(
            "sales", "account", "accountant", "recruiter", "recruiting", "talent", "marketing", "legal", "finance",
            "counsel", "product manager", "customer success",
        ),
        val runOnStartup: Boolean = false,
    )
}
