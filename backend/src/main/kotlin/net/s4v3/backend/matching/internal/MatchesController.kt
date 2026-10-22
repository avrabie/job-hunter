package net.s4v3.backend.matching.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.s4v3.backend.accounts.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The caller's own matches. The user is always the token's `sub`; no parameter names a user. */
@RestController
@Tag(name = "Matches", description = "Postings that fit the caller's profile")
internal class MatchesController(
    private val store: MatchStore,
    private val currentUser: CurrentUser,
) {

    @GetMapping("/api/matches")
    @Operation(
        summary = "List the caller's matching postings",
        description = "Passing results of the prefilter, best score first, one page at a time (size at most 100).",
        responses = [
            ApiResponse(responseCode = "200", description = "A page of matches; each has the posting, a score and reason codes"),
            ApiResponse(responseCode = "400", description = "page is negative"),
            ApiResponse(responseCode = "401", description = "No valid access token"),
        ],
    )
    fun matches(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<MatchItem>> {
        if (page < 0) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(store.passing(currentUser.id(), page, size.coerceIn(1, MAX_SIZE)))
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
