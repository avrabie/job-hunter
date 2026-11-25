package net.s4v3.backend.admin.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Administrator view of the users: account, profile contact, and the jobs each one saved or is applying to. */
@RestController
@Tag(name = "Admin: users", description = "Users, their saved and applied jobs (ADMIN role)")
internal class AdminUsersController(private val repository: AdminUserRepository) {

    @GetMapping("/api/admin/users")
    @Operation(
        summary = "List users",
        description = "Every user with the number of tracked jobs per stage (saved = INTERESTED, applying = APPLYING, applied, rejected) " +
            "and the time of the last stage change. Newest sign-up first. `limit` is 1 to 500, default 200.",
        responses = [
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(responseCode = "403", description = "Not an administrator", content = [Content()]),
        ],
    )
    fun users(@RequestParam(defaultValue = "200") limit: Int): List<AdminUserSummary> =
        repository.summaries(sub = null, limit = limit.coerceIn(1, MAX_LIMIT))

    @GetMapping("/api/admin/users/{sub}")
    @Operation(
        summary = "One user with profile contact and tracked jobs",
        description = "The account, the profile's name, headline, phone, website, LinkedIn and city, and every tracked job " +
            "with its stage, title, company and link. Descriptions and notes are not included.",
        responses = [
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(responseCode = "403", description = "Not an administrator", content = [Content()]),
            ApiResponse(responseCode = "404", description = "No user has this id", content = [Content()]),
        ],
    )
    fun user(@PathVariable sub: String): ResponseEntity<AdminUserDetail> {
        val summary = repository.summaries(sub = sub, limit = 1).firstOrNull()
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            AdminUserDetail(
                user = summary,
                profile = repository.profile(sub),
                applications = repository.applications(sub, limit = MAX_APPLICATIONS),
            ),
        )
    }

    private companion object {
        const val MAX_LIMIT = 500
        const val MAX_APPLICATIONS = 500
    }
}
