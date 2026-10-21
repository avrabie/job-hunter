package net.s4v3.backend.web.internal

import jakarta.servlet.http.HttpServletRequest
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.profile.ProfileConflictException
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileService
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
internal class ProfileController(
    private val profiles: ProfileService,
    private val currentUser: CurrentUser,
) {

    @GetMapping("/api/profile")
    fun get(): StoredProfile = profiles.get(currentUser.id())

    /** The body is read by hand so the size limit applies before anything is parsed. */
    @PutMapping("/api/profile", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun put(request: HttpServletRequest): ResponseEntity<Any> {
        val limit = ProfileService.MAX_BODY_BYTES
        if (request.contentLengthLong > limit) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build()
        val body = request.inputStream.readNBytes(limit + 1)
        if (body.size > limit) return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build()

        val update = profiles.parse(body)
        return ResponseEntity.ok(profiles.replace(currentUser.id(), update))
    }
}

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
