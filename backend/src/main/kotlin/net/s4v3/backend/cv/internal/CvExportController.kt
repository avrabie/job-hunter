package net.s4v3.backend.cv.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.ExportBusyException
import net.s4v3.backend.cv.export.ExportFailedException
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.ExportService
import net.s4v3.backend.cv.export.InvalidPhotoException
import net.s4v3.backend.cv.export.NameRequiredException
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.util.UUID

/**
 * Renders the caller's saved profile as Markdown, HTML or PDF. The profile is read by the token's `sub`, never from the
 * request. Nothing here logs the body, the name or any profile text.
 */
@RestController
@Tag(name = "CV", description = "Exports the saved profile as a CV in Markdown, HTML or PDF")
internal class CvExportController(
    private val exports: ExportService,
    private val limiter: ExportLimiter,
    private val currentUser: CurrentUser,
) {

    @PostMapping("/api/cv/export", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Export the saved profile as a CV",
        description = "Renders the caller's saved profile (never a profile from the request) in the chosen format, template " +
            "and language, and answers with the file bytes as an attachment. `sections` selects and orders the sections " +
            "(default: all). With the template `PROFESSIONAL` and the format HTML or PDF, `photoFileId` (the id of one of the " +
            "caller's own JPEG files) adds a round photo; it is ignored with other templates or Markdown. Needs `contact.fullName` in the saved profile. The profile is not changed. At most 30 exports " +
            "per hour. Failures answer with a fixed `error` code.",
        responses = [
            ApiResponse(responseCode = "200", description = "The file (text/markdown, text/html or application/pdf)", content = [Content()]),
            ApiResponse(
                responseCode = "400",
                description = "`invalid_request`: unknown value, unknown field or not JSON; `photo_invalid`: `photoFileId` is not " +
                    "one of the caller's JPEG files (unknown, someone else's, not a JPEG by content, too large or damaged)",
                content = [Content(schema = Schema(implementation = ExportErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "409", description = "`name_required`: no full name in the saved profile, or no saved profile",
                content = [Content(schema = Schema(implementation = ExportErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "429", description = "`too_many_exports`",
                content = [Content(schema = Schema(implementation = ExportErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "500", description = "`render_failed`",
                content = [Content(schema = Schema(implementation = ExportErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "503", description = "`busy`: PDF rendering is at its limit",
                content = [Content(schema = Schema(implementation = ExportErrorBody::class))],
            ),
        ],
    )
    fun export(@RequestBody(required = false) body: ByteArray?): ResponseEntity<Any> {
        val request = parse(body) ?: return codeError(HttpStatus.BAD_REQUEST, "invalid_request")
        val sections = request.sections?.map { it ?: return codeError(HttpStatus.BAD_REQUEST, "invalid_request") }
        val sub = currentUser.id()
        if (!limiter.tryAcquire(sub)) return codeError(HttpStatus.TOO_MANY_REQUESTS, "too_many_exports")
        val options = ExportOptions(
            format = request.format, template = request.template, language = request.language, sections = sections,
            includeOpenSource = request.includeOpenSource, includeAnalysis = request.includeAnalysis,
            includeWorkAuthorisation = request.includeWorkAuthorisation, photoFileId = request.photoFileId,
        )
        return try {
            val cv = exports.export(sub, options)
            ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(cv.contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${cv.fileName}\"")
                .cacheControl(CacheControl.noStore())
                .body(cv.bytes)
        } catch (_: InvalidPhotoException) {
            codeError(HttpStatus.BAD_REQUEST, "photo_invalid")
        } catch (_: NameRequiredException) {
            codeError(HttpStatus.CONFLICT, "name_required")
        } catch (_: ExportBusyException) {
            codeError(HttpStatus.SERVICE_UNAVAILABLE, "busy")
        } catch (_: ExportFailedException) {
            codeError(HttpStatus.INTERNAL_SERVER_ERROR, "render_failed")
        }
    }

    private fun parse(body: ByteArray?): ExportRequest? =
        try {
            body?.let { strict.readValue(it, ExportRequest::class.java) }
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

/** The request body. There is no profile and no user id here, so an unknown field such as `profile` is refused. */
internal data class ExportRequest(
    val format: CvFormat,
    val template: CvTemplate,
    val language: CvLanguage,
    val sections: List<CvSectionKind?>? = null,
    val includeOpenSource: Boolean = true,
    val includeAnalysis: Boolean = false,
    val includeWorkAuthorisation: Boolean = true,
    val photoFileId: UUID? = null,
)

/** Shape of an error answer, for the API description. */
internal data class ExportErrorBody(val error: String)
