package net.s4v3.backend.cv.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.cv.publiccv.PublicCvService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** Administrator takedown of a public CV page. The ADMIN rule for the admin paths is in the security configuration. */
@RestController
@Tag(name = "Admin", description = "Administrator operations (ADMIN role)")
internal class PublicCvAdminController(private val service: PublicCvService) {

    @DeleteMapping("/api/admin/public-cv/{handle}")
    @Operation(
        summary = "Take down a public CV page",
        description = "Pauses the page and locks it, so that its owner cannot publish again until it is unlocked.",
        responses = [
            ApiResponse(responseCode = "204", description = "Taken down", content = [Content()]),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(responseCode = "403", description = "Not an administrator", content = [Content()]),
            ApiResponse(
                responseCode = "404", description = "`not_found`: no page has the handle",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun takeDown(@PathVariable handle: String): ResponseEntity<Any> = answer(service.takeDown(handle))

    @PostMapping("/api/admin/public-cv/{handle}/unlock")
    @Operation(
        summary = "Unlock a public CV page",
        description = "Lifts the lock of a takedown. The page stays paused until its owner publishes it again.",
        responses = [
            ApiResponse(responseCode = "204", description = "Unlocked", content = [Content()]),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(responseCode = "403", description = "Not an administrator", content = [Content()]),
            ApiResponse(
                responseCode = "404", description = "`not_found`: no page has the handle",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun unlock(@PathVariable handle: String): ResponseEntity<Any> = answer(service.unlock(handle))

    private fun answer(found: Boolean): ResponseEntity<Any> =
        if (found) ResponseEntity.noContent().build() else codeError(HttpStatus.NOT_FOUND, "not_found")
}
