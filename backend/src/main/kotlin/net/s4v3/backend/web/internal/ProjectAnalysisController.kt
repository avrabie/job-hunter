package net.s4v3.backend.web.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.llm.ProjectAnalyst
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.DigestResult
import net.s4v3.backend.profile.ProfileService
import net.s4v3.backend.profile.ProjectAnalysis
import net.s4v3.backend.profile.RepositoryDigestSource
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

private val ownerRepoSegment = Regex("[A-Za-z0-9._-]{1,100}")

/**
 * Analyses one saved open source project: reads its repository, asks the model, and returns the checked analysis. Only a
 * project in the caller's own saved profile is analysed. The profile is never changed. Nothing here logs the name, the
 * repository text or the answer.
 */
@RestController
@Tag(name = "Profile", description = "The caller's own master profile")
internal class ProjectAnalysisController(
    private val profiles: ProfileService,
    private val currentUser: CurrentUser,
    private val digests: RepositoryDigestSource,
    private val analyst: ProjectAnalyst,
    private val limiter: AnalysisLimiter,
) {

    private val log = LoggerFactory.getLogger(ProjectAnalysisController::class.java)

    @PostMapping("/api/profile/open-source-projects/analyze", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Analyse a saved open source project",
        description = "Reads the repository (its file tree, README and a few build files) and describes it with a language model. " +
            "Only a project in the caller's saved profile is analysed. Every technology cites a file of the repository, and " +
            "one that does not appear in it is left out. The profile is not changed: the client keeps the analysis and saves it. " +
            "At most a few analyses per hour. Failures answer with a fixed `error` code.",
        responses = [
            ApiResponse(
                responseCode = "200", description = "The analysis",
                content = [Content(schema = Schema(implementation = AnalysisBody::class))],
            ),
            ApiResponse(
                responseCode = "400", description = "`invalid_request`: unknown provider or malformed name",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(responseCode = "401", description = "No valid access token", content = [Content()]),
            ApiResponse(
                responseCode = "404", description = "`project_not_found` (not in the saved profile) or `repository_not_found`",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "429", description = "`too_many_analyses` or `provider_rate_limited`",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "502", description = "`provider_unavailable` or `analysis_failed`",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "503", description = "`analysis_not_configured`: no model key on the server",
                content = [Content(schema = Schema(implementation = ErrorBody::class))],
            ),
        ],
    )
    fun analyze(@RequestBody(required = false) request: AnalyzeRequest?): ResponseEntity<Any> {
        val provider = CodeProvider.entries.firstOrNull { it.name == request?.provider }
        val fullName = request?.fullName
        if (provider == null || fullName == null || !isOwnerRepo(fullName)) return error(HttpStatus.BAD_REQUEST, "invalid_request")

        val sub = currentUser.id()
        val saved = profiles.get(sub).profile.openSourceProjects
            .any { it.provider == provider && it.fullName.equals(fullName, ignoreCase = true) }
        if (!saved) return error(HttpStatus.NOT_FOUND, "project_not_found")
        if (!limiter.tryAcquire(sub)) return error(HttpStatus.TOO_MANY_REQUESTS, "too_many_analyses")

        val digest = when (val answer = digests.digest(provider, fullName)) {
            is DigestResult.Found -> answer.digest
            DigestResult.InvalidName -> return error(HttpStatus.BAD_REQUEST, "invalid_request")
            DigestResult.NotFound -> return error(HttpStatus.NOT_FOUND, "repository_not_found")
            DigestResult.RateLimited -> return error(HttpStatus.TOO_MANY_REQUESTS, "provider_rate_limited")
            DigestResult.Unavailable -> return error(HttpStatus.BAD_GATEWAY, "provider_unavailable")
        }
        return try {
            val started = System.nanoTime()
            val analysis = analyst.analyze(digest)
            log.info("Project analysis finished in {} ms", (System.nanoTime() - started) / 1_000_000)
            ResponseEntity.ok(AnalysisBody(analysis))
        } catch (e: LlmFailure) {
            when (e.kind) {
                LlmFailureKind.NOT_CONFIGURED -> error(HttpStatus.SERVICE_UNAVAILABLE, "analysis_not_configured")
                LlmFailureKind.FAILED -> error(HttpStatus.BAD_GATEWAY, "analysis_failed")
            }
        }
    }

    private fun error(status: HttpStatus, code: String): ResponseEntity<Any> =
        ResponseEntity.status(status).body(mapOf("error" to code))

    /** Same rule as the digest source: `owner/repo`, each part 1 to 100 letters, digits, dots, dashes or underscores, no dot segments. */
    private fun isOwnerRepo(fullName: String): Boolean {
        val parts = fullName.split('/')
        return parts.size == 2 && parts.all { it != "." && it != ".." && ownerRepoSegment.matches(it) }
    }
}

/** The request body: the provider's name and `owner/repo`. */
internal data class AnalyzeRequest(val provider: String? = null, val fullName: String? = null)

/** Shape of a successful answer, for the API description and the body. */
internal data class AnalysisBody(val analysis: ProjectAnalysis)
