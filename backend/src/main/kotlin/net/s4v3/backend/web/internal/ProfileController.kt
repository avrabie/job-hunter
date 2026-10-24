package net.s4v3.backend.web.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.profile.ProfileConflictException
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.ProfileUpdate
import net.s4v3.backend.profile.StoredProfile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * The caller's master profile. The user is always the token's `sub`; no path or parameter names a user.
 * Nothing here logs the body or any part of it.
 */
@RestController
@Tag(name = "Profile", description = "The caller's own master profile")
internal class ProfileController(
    private val profiles: ProfileService,
    private val currentUser: CurrentUser,
) {

    @GetMapping("/api/profile")
    @Operation(
        summary = "Read the caller's master profile",
        description = "Returns the stored profile and its version, or an empty profile at version 0 if none is stored.",
        responses = [
            ApiResponse(responseCode = "200", description = "The profile and its version"),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
        ],
    )
    fun get(): StoredProfile = profiles.get(currentUser.id())

    /** The body is read by hand so the size limit applies before anything is parsed. */
    @PutMapping("/api/profile", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Replace the caller's master profile",
        description = "Replaces the whole profile if `version` equals the stored version; the answer holds the new version.",
        requestBody = RequestBody(
            required = true,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ProfileUpdate::class))],
        ),
        responses = [
            ApiResponse(
                responseCode = "200", description = "The stored profile with its new version",
                content = [Content(schema = Schema(implementation = StoredProfile::class))],
            ),
            ApiResponse(
                responseCode = "400", description = "A rule is broken; `fields` maps each field path to a message",
                content = [Content(schema = Schema(implementation = ValidationErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "409", description = "The stored version differs from `version`",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(responseCode = "413", description = "The body is larger than 256 KiB", content = [Content()]),
        ],
    )
    fun put(request: HttpServletRequest): ResponseEntity<Any> {
        val limit = ProfileService.MAX_BODY_BYTES
        if (request.contentLengthLong > limit) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build()
        val body = request.inputStream.readNBytes(limit + 1)
        if (body.size > limit) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build()

        val update = profiles.parse(body)
        return ResponseEntity.ok(profiles.replace(currentUser.id(), update))
    }
}

/** Shapes of the error bodies, for the API description only. */
internal data class ErrorBody(val error: String)

internal data class ValidationErrorBody(val error: String, val fields: Map<String, String>)

/** Error bodies for the profile endpoints. Messages come from the rules, never from the submitted values. */
@RestControllerAdvice(assignableTypes = [ProfileController::class])
internal class ProfileErrors {

    @ExceptionHandler
    fun invalid(e: ProfileInvalidException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.badRequest().body(mapOf("error" to "validation_failed", "fields" to e.fields))

    @ExceptionHandler
    fun conflict(e: ProfileConflictException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "version_conflict"))
}
