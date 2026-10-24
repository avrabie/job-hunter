package net.s4v3.backend.web.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.Repository
import net.s4v3.backend.profile.RepositoryCatalog
import net.s4v3.backend.profile.RepositoryListing
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Public repositories of a code account, for picking open source projects. Reads and writes no profile. The
 * username is checked before any outside call. Nothing here logs the input or the answer.
 */
@RestController
@Tag(name = "Profile", description = "The caller's own master profile")
internal class RepositoriesController(private val repositories: RepositoryCatalog) {

    @GetMapping("/api/profile/repositories")
    @Operation(
        summary = "List the public repositories of a code account",
        description = "Forks and archived repositories are left out; at most 100, newest push first. " +
            "Failures answer with a fixed `error` code.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "The repositories",
                content = [Content(schema = Schema(implementation = RepositoriesBody::class))],
            ),
            ApiResponse(
                responseCode = "400", description = "`invalid_username` or `unknown_provider`",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "404", description = "The account does not exist (`account_not_found`)",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "429", description = "The provider's rate limit is used up (`provider_rate_limited`)",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "502", description = "The provider timed out or failed (`provider_unavailable`)",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
        ],
    )
    fun list(
        @RequestParam(defaultValue = "") provider: String,
        @RequestParam(defaultValue = "") username: String,
    ): ResponseEntity<Any> {
        val code = CodeProvider.entries.firstOrNull { it.name == provider }
            ?: return codeError(HttpStatus.BAD_REQUEST, "unknown_provider")
        return when (val listing = repositories.list(code, username)) {
            is RepositoryListing.Found -> ResponseEntity.ok(
                RepositoriesBody(listing.repositories.map { RepositoryJson.of(it) }),
            )
            RepositoryListing.InvalidUsername -> codeError(HttpStatus.BAD_REQUEST, "invalid_username")
            RepositoryListing.NotFound -> codeError(HttpStatus.NOT_FOUND, "account_not_found")
            RepositoryListing.RateLimited -> codeError(HttpStatus.TOO_MANY_REQUESTS, "provider_rate_limited")
            RepositoryListing.Unavailable -> codeError(HttpStatus.BAD_GATEWAY, "provider_unavailable")
        }
    }
}

/** Shape of a successful answer, for the API description and the body. */
internal data class RepositoriesBody(val repositories: List<RepositoryJson>)

/** One repository as the API shows it. */
internal data class RepositoryJson(
    val provider: String,
    val fullName: String,
    val name: String,
    val url: String,
    val description: String?,
    val language: String?,
    val topics: List<String>,
    val stars: Int?,
    val pushedAt: Instant?,
) {
    companion object {
        fun of(repository: Repository) = RepositoryJson(
            provider = repository.provider.name,
            fullName = repository.fullName,
            name = repository.name,
            url = repository.url,
            description = repository.description,
            language = repository.language,
            topics = repository.topics,
            stars = repository.stars,
            pushedAt = repository.pushedAt,
        )
    }
}
