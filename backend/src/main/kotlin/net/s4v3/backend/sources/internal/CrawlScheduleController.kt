package net.s4v3.backend.sources.internal

import net.s4v3.backend.accounts.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

internal data class ScheduleRequest(val enabled: Boolean? = null, val cron: String? = null, val zone: String? = null)

internal data class ScheduleView(
    val enabled: Boolean,
    val cron: String,
    val zone: String,
    val nextFire: Instant?,
    val updatedAt: Instant?,
)

/** Read and change the crawl schedule. Access is limited to `ADMIN` by the security chain for everything under `/api/admin`. */
@RestController
internal class CrawlScheduleController(
    private val store: ScheduleStore,
    private val settings: SourcesSettings,
    private val currentUser: CurrentUser,
) {

    private val log = LoggerFactory.getLogger(CrawlScheduleController::class.java)

    @GetMapping("/api/admin/crawl-schedule")
    fun get(): ScheduleView = view(store.current())

    @PostMapping("/api/admin/crawl-schedule")
    fun post(@RequestBody body: ScheduleRequest): ResponseEntity<Any> {
        val error = when {
            body.enabled == null -> ScheduleFieldError("enabled", "enabled is required")
            else -> ScheduleRules.validate(body.cron, body.zone, settings.minInterval)
        }
        if (error != null) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "validation_failed", "field" to error.field, "message" to error.message))
        }
        val saved = store.save(body.enabled!!, body.cron!!.trim(), body.zone!!.trim(), currentUser.id())
        log.info("Crawl schedule changed: enabled={}, cron={}", saved.enabled, saved.cron)
        return ResponseEntity.ok(view(saved))
    }

    private fun view(s: CrawlSchedule) = ScheduleView(
        s.enabled, s.cron, s.zone,
        if (s.enabled) ScheduleRules.nextFire(s.cron, s.zone, Instant.now()) else null,
        s.updatedAt,
    )
}
