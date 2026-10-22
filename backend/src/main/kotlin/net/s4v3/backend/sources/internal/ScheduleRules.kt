package net.s4v3.backend.sources.internal

import org.springframework.scheduling.support.CronExpression
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** One rejected schedule field; [message] is fixed text and never echoes the submitted value. */
internal data class ScheduleFieldError(val field: String, val message: String)

/** Parsing, validation and fire times of a crawl schedule. */
internal object ScheduleRules {

    /** The first error found, or null when [cron] and [zone] make a usable schedule with fires at least [minGap] apart. */
    fun validate(cron: String?, zone: String?, minGap: Duration): ScheduleFieldError? {
        if (cron.isNullOrBlank()) return ScheduleFieldError("cron", "cron is required")
        if (zone.isNullOrBlank()) return ScheduleFieldError("zone", "zone is required")
        val id = try {
            ZoneId.of(zone)
        } catch (e: Exception) {
            return ScheduleFieldError("zone", "zone is not a known time zone")
        }
        if (!CronExpression.isValidExpression(cron)) return ScheduleFieldError("cron", "cron is not a valid cron expression")
        val first = CronExpression.parse(cron).next(Instant.now().atZone(id)) ?: return null
        val second = CronExpression.parse(cron).next(first) ?: return null
        if (Duration.between(first, second) < minGap) {
            return ScheduleFieldError("cron", "cron fires more often than every ${minGap.toMinutes()} minutes")
        }
        return null
    }

    /** The next fire time after [after], or null when the cron never fires again. The schedule must be valid. */
    fun nextFire(cron: String, zone: String, after: Instant): Instant? =
        CronExpression.parse(cron).next(after.atZone(ZoneId.of(zone)))?.toInstant()
}
