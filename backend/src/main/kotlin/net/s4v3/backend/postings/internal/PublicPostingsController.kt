package net.s4v3.backend.postings.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import net.s4v3.backend.postings.Posting
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.postings.PostingSearch
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

/** One source link of a public posting: the label of the source and the URL of the original page. */
internal data class PublicLink(val label: String, val url: String)

/** What the public list shows of a posting. A separate type on purpose: no description, raw payload or last-seen time. */
internal data class PublicPosting(
    val id: UUID,
    val title: String,
    val company: String,
    val city: String?,
    val canton: String?,
    val remoteMode: String?,
    val salaryMin: Int?,
    val salaryMax: Int?,
    val language: String,
    val firstSeen: Instant,
    val links: List<PublicLink>,
)

/** One page of the public list; [total] is the number of postings in the filtered window. */
internal data class PublicPostingsPage(val total: Long, val page: Int, val size: Int, val postings: List<PublicPosting>)

/**
 * The newest postings for anyone, without a token: metadata and links back to the source only. Limited per client address
 * (the first `X-Forwarded-For` value, else the peer address). Nothing the caller sends and no address is logged.
 */
@RestController
@Tag(name = "Public postings", description = "The newest crawled postings, metadata and source links only, no sign-in")
internal class PublicPostingsController(
    private val postings: PostingQuery,
    private val limiter: PublicPostingsLimiter,
    private val clock: Clock = Clock.systemUTC(),
) {

    @GetMapping("/api/public/postings")
    @Operation(
        summary = "List the newest postings without signing in",
        description = "No token needed. Newest first-seen first, ties by id. Only title, company, city, canton, remote mode, " +
            "salary range, language, first-seen time and the links to the original pages are given; the description stays " +
            "behind sign-in. `size` defaults to 20 (at most 50, larger is cut to 50). The window is the last `days` days " +
            "(1 to 31) or `since` to `until` (ISO dates in Zurich time, both inclusive, at most 31 days, `since` at most " +
            "90 days ago); without a date it is the last 14 days. `page` times `size` must not exceed 1000. `canton` is a " +
            "two-letter code, `language` is `en` or `de`. `total` counts the postings in the window. Cached for 60 seconds. " +
            "Limited per client address (default 60 requests per minute).",
        responses = [
            ApiResponse(responseCode = "200", description = "A page of postings with total, page and size"),
            ApiResponse(responseCode = "400", description = "A parameter is invalid, the window is too long or too old, or page times size is above 1000"),
            ApiResponse(responseCode = "429", description = "Too many requests from this address; see `Retry-After`"),
        ],
    )
    fun list(
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) size: String?,
        @RequestParam(required = false) days: String?,
        @RequestParam(required = false) since: String?,
        @RequestParam(required = false) until: String?,
        @RequestParam(required = false) canton: String?,
        @RequestParam(required = false) language: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val client = clientAddress(request)
        if (!limiter.tryAcquire(client)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, limiter.retryAfterSeconds(client).toString())
                .cacheControl(CacheControl.noStore())
                .body(mapOf("error" to "rate_limited"))
        }
        val pageNumber = page.orEmpty().ifEmpty { "0" }.toIntOrNull()?.takeIf { it >= 0 } ?: return invalid("page")
        val pageSize = (size.orEmpty().ifEmpty { "20" }.toIntOrNull()?.takeIf { it >= 1 } ?: return invalid("size")).coerceAtMost(MAX_SIZE)
        if (pageNumber.toLong() * pageSize > MAX_OFFSET) return invalid("page")
        if (canton.orEmpty().isNotEmpty() && !CANTON.matches(canton!!)) return invalid("canton")
        val lang = language.orEmpty().lowercase().ifEmpty { null }
        if (lang != null && lang !in LANGUAGES) return invalid("language")

        val window = window(days.orEmpty(), since.orEmpty(), until.orEmpty())
        if (window.invalid != null) return invalid(window.invalid)

        val search = PostingSearch(
            canton = canton?.uppercase()?.takeIf { it.isNotEmpty() }, language = lang,
            firstSeenFrom = window.from, firstSeenBefore = window.before, page = pageNumber, size = pageSize,
        )
        val body = PublicPostingsPage(postings.count(search), pageNumber, pageSize, postings.search(search).map(::view))
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()).body(body)
    }

    private class Window(val from: Instant? = null, val before: Instant? = null, val invalid: String? = null)

    private fun window(days: String, since: String, until: String): Window {
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, ZURICH)
        if (days.isNotEmpty() && (since.isNotEmpty() || until.isNotEmpty())) return Window(invalid = "days")
        if (days.isNotEmpty()) {
            val n = days.toIntOrNull()?.takeIf { it in 1..MAX_WINDOW_DAYS } ?: return Window(invalid = "days")
            return Window(from = now.minus(n.toLong(), ChronoUnit.DAYS))
        }
        if (since.isEmpty() && until.isEmpty()) return Window(from = now.minus(DEFAULT_DAYS, ChronoUnit.DAYS))
        val last = if (until.isEmpty()) today else date(until) ?: return Window(invalid = "until")
        val first = if (since.isEmpty()) last.minusDays(MAX_WINDOW_DAYS - 1L) else date(since) ?: return Window(invalid = "since")
        if (first.isBefore(today.minusDays(MAX_AGE_DAYS))) return Window(invalid = "since")
        if (last.isBefore(first) || ChronoUnit.DAYS.between(first, last) >= MAX_WINDOW_DAYS) return Window(invalid = "until")
        return Window(
            from = first.atStartOfDay(ZURICH).toInstant(),
            before = last.plusDays(1).atStartOfDay(ZURICH).toInstant(),
        )
    }

    private fun date(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun invalid(field: String): ResponseEntity<Any> =
        ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("error" to "invalid_parameter", "field" to field))

    private fun view(p: Posting) = PublicPosting(
        p.id, p.title, p.company, p.city, p.canton, p.remoteMode?.name, p.salaryMin, p.salaryMax, p.language, p.firstSeen,
        p.links.map { PublicLink(it.sourceId, it.url) },
    )

    /** The first `X-Forwarded-For` value (set by the edge), else the peer address; cut to a safe length. Used as a key only. */
    private fun clientAddress(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim().orEmpty()
        return forwarded.ifEmpty { request.remoteAddr.orEmpty() }.take(MAX_ADDRESS)
    }

    private companion object {
        val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        val CANTON = Regex("[A-Za-z]{2}")
        val LANGUAGES = setOf("en", "de")
        const val MAX_SIZE = 50
        const val MAX_OFFSET = 1000L
        const val MAX_WINDOW_DAYS = 31
        const val DEFAULT_DAYS = 14L
        const val MAX_AGE_DAYS = 90L
        const val MAX_ADDRESS = 64
    }
}
