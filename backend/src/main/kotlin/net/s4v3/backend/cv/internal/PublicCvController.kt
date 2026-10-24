package net.s4v3.backend.cv.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.publiccv.HandleUnavailable
import net.s4v3.backend.cv.publiccv.PublicCvContact
import net.s4v3.backend.cv.publiccv.PublicCvService
import net.s4v3.backend.cv.publiccv.PublicCvSettings
import net.s4v3.backend.cv.publiccv.PublicCvState
import net.s4v3.backend.cv.publiccv.RemoveResult
import net.s4v3.backend.cv.publiccv.SaveResult
import net.s4v3.backend.cv.publiccv.internal.PublicCvLimits
import net.s4v3.backend.cv.publiccv.internal.WindowLimiter
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.Instant
import java.util.UUID
import kotlin.math.max

/**
 * The owner's endpoints for the public CV page. Everything is by the token's `sub`; no request names a user. Nothing here
 * logs a handle, a body or a setting.
 */
@RestController
@Tag(name = "Public CV", description = "The caller's own public CV page: handle, visibility settings, preview")
internal class PublicCvController(
    private val service: PublicCvService,
    private val limits: PublicCvLimits,
    private val currentUser: CurrentUser,
) {

    @GetMapping("/api/cv/public")
    @Operation(
        summary = "Read the caller's public CV page state",
        description = "Answers `{\"configured\":false}` when the caller has no page, otherwise the handle, the switches, the " +
            "settings and when the handle may be changed again.",
        responses = [
            ApiResponse(responseCode = "200", description = "The state", content = [Content(schema = Schema(implementation = PublicCvStateBody::class))]),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
        ],
    )
    fun get(): ResponseEntity<Any> = ResponseEntity.ok(body(service.state(currentUser.id())))

    @PutMapping("/api/cv/public", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Save the handle, the settings and the published switch",
        description = "Creates or changes the caller's page. `published: false` pauses it. Unknown fields and unknown values are " +
            "refused. The handle is lower-cased; a message never repeats it.",
        responses = [
            ApiResponse(responseCode = "200", description = "The saved state", content = [Content(schema = Schema(implementation = PublicCvStateBody::class))]),
            ApiResponse(
                responseCode = "400", description = "`validation_failed` with `fields`, `invalid_request`, or `photo_invalid` (`photoFileId` is not one of the caller's JPEG files)",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "409",
                description = "`handle_taken`, `handle_reserved`, `handle_held`, `handle_change_too_soon`, `name_required` or `locked`",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "429", description = "`rate_limited`: at most 30 saves and removals per hour",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun put(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = parse(body, SaveRequest::class.java) ?: return codeError(HttpStatus.BAD_REQUEST, "invalid_request")
        val settings = request.settings.toSettings() ?: return codeError(HttpStatus.BAD_REQUEST, "invalid_request")
        val sub = currentUser.id()
        if (!limits.saves.tryAcquire(sub)) return rateLimited(limits.saves, sub)
        return when (val result = service.save(sub, request.handle, request.published, settings)) {
            is SaveResult.Saved -> ResponseEntity.ok(body(result.state))
            is SaveResult.ValidationFailed ->
                ResponseEntity.badRequest().body(mapOf("error" to "validation_failed", "fields" to result.fields))
            SaveResult.Reserved -> codeError(HttpStatus.CONFLICT, "handle_reserved")
            SaveResult.Taken -> codeError(HttpStatus.CONFLICT, "handle_taken")
            SaveResult.Held -> codeError(HttpStatus.CONFLICT, "handle_held")
            is SaveResult.ChangeTooSoon -> codeError(HttpStatus.CONFLICT, "handle_change_too_soon")
            SaveResult.NameRequired -> codeError(HttpStatus.CONFLICT, "name_required")
            SaveResult.Locked -> codeError(HttpStatus.CONFLICT, "locked")
            SaveResult.PhotoInvalid -> codeError(HttpStatus.BAD_REQUEST, "photo_invalid")
        }
    }

    @DeleteMapping("/api/cv/public")
    @Operation(
        summary = "Remove the caller's public CV page",
        description = "Removes the settings and holds the handle for 90 days. Answers 204 also when there was no page. " +
            "A page locked by an administrator cannot be removed.",
        responses = [
            ApiResponse(responseCode = "204", description = "Removed, or nothing to remove", content = [Content()]),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "409", description = "`locked`",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "429", description = "`rate_limited`: at most 30 saves and removals per hour",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun delete(): ResponseEntity<Any> {
        val sub = currentUser.id()
        if (!limits.saves.tryAcquire(sub)) return rateLimited(limits.saves, sub)
        return when (service.remove(sub)) {
            RemoveResult.REMOVED, RemoveResult.NOT_FOUND -> ResponseEntity.noContent().build()
            RemoveResult.LOCKED -> codeError(HttpStatus.CONFLICT, "locked")
        }
    }

    @GetMapping("/api/cv/public/handle")
    @Operation(
        summary = "Check whether a handle is available",
        description = "`available` is true for a free handle and for the caller's own. Otherwise `reason` is `invalid`, " +
            "`reserved`, `taken` or `held`. At most 60 checks per hour.",
        responses = [
            ApiResponse(responseCode = "200", description = "The answer", content = [Content(schema = Schema(implementation = HandleCheckBody::class))]),
            ApiResponse(
                responseCode = "400", description = "`invalid_request`: no handle given",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "429", description = "`rate_limited`",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun check(@RequestParam(required = false) handle: String?): ResponseEntity<Any> {
        val sub = currentUser.id()
        if (handle == null) return codeError(HttpStatus.BAD_REQUEST, "invalid_request")
        if (!limits.handleChecks.tryAcquire(sub)) return rateLimited(limits.handleChecks, sub)
        val reason = service.checkHandle(sub, handle)
        return ResponseEntity.ok(
            if (reason == null) mapOf("available" to true) else mapOf("available" to false, "reason" to reason.name.lowercase()),
        )
    }

    @PostMapping("/api/cv/public/preview", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Prepare a preview of the page with the given settings",
        description = "Answers the path of a one-use ticket, valid for 60 seconds. Opening the path (no token) shows the page " +
            "as it would be with these settings, rendered from the saved profile. At most 30 previews per hour.",
        responses = [
            ApiResponse(responseCode = "200", description = "The path of the ticket", content = [Content(schema = Schema(implementation = PreviewPathBody::class))]),
            ApiResponse(
                responseCode = "400", description = "`invalid_request`: unknown field or value",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "429", description = "`rate_limited`",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun preview(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = parse(body, PreviewRequest::class.java) ?: return codeError(HttpStatus.BAD_REQUEST, "invalid_request")
        val settings = request.settings.toSettings() ?: return codeError(HttpStatus.BAD_REQUEST, "invalid_request")
        val sub = currentUser.id()
        if (!limits.previews.tryAcquire(sub)) return rateLimited(limits.previews, sub)
        val ticket = service.preview(sub, settings)
        return ResponseEntity.ok(mapOf("path" to "/api/cv/public/preview/" + ticket.value))
    }

    private fun rateLimited(limiter: WindowLimiter, key: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", max(1L, limiter.retryAfter(key).toSeconds()).toString())
            .body(mapOf("error" to "rate_limited"))

    private fun body(state: PublicCvState?): Any =
        if (state == null) {
            mapOf("configured" to false)
        } else {
            linkedMapOf(
                "configured" to true,
                "handle" to state.handle,
                "published" to state.published,
                "locked" to state.locked,
                "settings" to state.settings,
                "handleChangeableAt" to state.handleChangeableAt,
                "updatedAt" to state.updatedAt,
            )
        }

    private fun <T> parse(body: ByteArray?, type: Class<T>): T? =
        try {
            body?.let { strict.readValue(it, type) }
        } catch (_: JacksonException) {
            // The exception message quotes the input, so it is dropped.
            null
        }

    private companion object {
        val strict: JsonMapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()
    }
}

/** The body of `PUT /api/cv/public`. There is no user id here, so one is refused as an unknown field. */
internal data class SaveRequest(val handle: String, val published: Boolean, val settings: SettingsRequest = SettingsRequest())

internal data class PreviewRequest(val settings: SettingsRequest)

/** Settings as sent; a null section is refused by [toSettings]. */
internal data class SettingsRequest(
    val template: CvTemplate = CvTemplate.CLASSIC,
    val language: CvLanguage = CvLanguage.EN,
    val sections: List<CvSectionKind?> = emptyList(),
    val headline: Boolean = false,
    val analysis: Boolean = false,
    val workAuthorisation: Boolean = false,
    val location: Boolean = false,
    val contact: ContactRequest = ContactRequest(),
    val indexable: Boolean = false,
    val photoFileId: UUID? = null,
) {
    /** Null when a section is null. A repeated section is kept once, at its first position. */
    fun toSettings(): PublicCvSettings? {
        if (sections.any { it == null }) return null
        return PublicCvSettings(
            template, language, sections.filterNotNull().distinct(), headline, analysis, workAuthorisation, location,
            PublicCvContact(contact.email, contact.phone, contact.website, contact.linkedin, contact.codeAccounts), indexable, photoFileId,
        )
    }
}

internal data class ContactRequest(
    val email: Boolean = false,
    val phone: Boolean = false,
    val website: Boolean = false,
    val linkedin: Boolean = false,
    val codeAccounts: Boolean = false,
)

/** Shapes of the answers, for the API description. */
internal data class PublicCvErrorBody(val error: String, val fields: Map<String, String>? = null)

internal data class PublicCvStateBody(
    val configured: Boolean,
    val handle: String? = null,
    val published: Boolean? = null,
    val locked: Boolean? = null,
    val settings: PublicCvSettings? = null,
    val handleChangeableAt: Instant? = null,
    val updatedAt: Instant? = null,
)

internal data class HandleCheckBody(val available: Boolean, val reason: HandleUnavailable? = null)

internal data class PreviewPathBody(val path: String)
