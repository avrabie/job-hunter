package net.s4v3.backend.sources.internal

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

/** [updatedAt] and [updatedBy] are null while the schedule still comes from the settings. */
internal data class CrawlSchedule(
    val enabled: Boolean,
    val cron: String,
    val zone: String,
    val updatedAt: Instant? = null,
    val updatedBy: String? = null,
)

/** What the scheduler reads; lets tests drive it without a database. */
internal fun interface ScheduleSource {
    fun current(): CrawlSchedule
}

/** The crawl schedule: the stored row if there is one, else the initial values from the settings. */
@Component
internal class ScheduleStore(
    private val jdbc: JdbcClient,
    private val settings: SourcesSettings,
) : ScheduleSource, SmartInitializingSingleton {

    /** Stops startup, naming the setting, when the settings enable a schedule that cannot work and none is stored. */
    override fun afterSingletonsInstantiated() {
        val initial = settings.schedule
        if (!initial.enabled || stored() != null) return
        val error = ScheduleRules.validate(initial.cron, initial.zone, java.time.Duration.ZERO) ?: return
        throw IllegalStateException("Invalid setting jobhunter.sources.schedule.${error.field}: ${error.message}")
    }

    override fun current(): CrawlSchedule =
        stored() ?: settings.schedule.let { CrawlSchedule(it.enabled, it.cron, it.zone) }

    fun save(enabled: Boolean, cron: String, zone: String, by: String, at: Instant = Instant.now()): CrawlSchedule {
        jdbc.sql(
            """
            INSERT INTO crawl_schedule (id, enabled, cron, zone, updated_at, updated_by)
            VALUES (1, :enabled, :cron, :zone, :at, :by)
            ON CONFLICT (id) DO UPDATE SET enabled = EXCLUDED.enabled, cron = EXCLUDED.cron, zone = EXCLUDED.zone,
                                           updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by
            """.trimIndent(),
        ).param("enabled", enabled).param("cron", cron).param("zone", zone).param("at", Timestamp.from(at))
            .param("by", by).update()
        return current()
    }

    private fun stored(): CrawlSchedule? =
        jdbc.sql("SELECT enabled, cron, zone, updated_at, updated_by FROM crawl_schedule WHERE id = 1")
            .query { rs, _ ->
                CrawlSchedule(
                    rs.getBoolean(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getString(5),
                )
            }.optional().orElse(null)
}
