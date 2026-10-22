package net.s4v3.backend.sources.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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

/** Shape of the 400 body, for the API description only. */
internal data class ScheduleErrorBody(val error: String, val field: String, val message: String)

internal data class ScheduleView(
    val enabled: Boolean,
    val cron: String,
    val zone: String,
    val nextFire: Instant?,
    val updatedAt: Instant?,
)

/** Read and change the crawl schedule. Access is limited to `ADMIN` by the security chain for everything under `/api/admin`. */
@RestController
@Tag(name = "Admin: crawl schedule", description = "The shared crawl schedule. Requires the ADMIN role.")
internal class CrawlScheduleController(
    private val store: ScheduleStore,
    private val settings: SourcesSettings,
    private val currentUser: CurrentUser,
) {

    private val log = LoggerFactory.getLogger(CrawlScheduleController::class.java)

    @GetMapping("/api/admin/crawl-schedule")
    @Operation(
        summary = "Read the crawl schedule",
        description = "Requires the ADMIN role. `nextFire` is absent while the schedule is disabled.",
        responses = [
            ApiResponse(responseCode = "200", description = "The current schedule"),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(responseCode = "403", description = "The caller lacks the ADMIN role", content = [Content()]),
        ],
    )
    fun get(): ScheduleView = view(store.current())

    @PostMapping("/api/admin/crawl-schedule")
    @Operation(
        summary = "Change the crawl schedule",
        description = "Requires the ADMIN role. Stores the schedule; it takes effect within a minute without a restart.",
        responses = [
            ApiResponse(responseCode = "200", description = "The stored schedule"),
            ApiResponse(
                responseCode = "400", description = "Invalid cron, zone or missing field, or a cron faster than every 15 minutes",
                content = [Content(schema = Schema(implementation = ScheduleErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(responseCode = "403", description = "The caller lacks the ADMIN role", content = [Content()]),
        ],
    )
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
