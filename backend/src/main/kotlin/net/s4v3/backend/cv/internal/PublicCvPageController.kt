package net.s4v3.backend.cv.internal

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import net.s4v3.backend.cv.publiccv.PreviewTicket
import net.s4v3.backend.cv.publiccv.PublicCvService
import net.s4v3.backend.cv.publiccv.RenderedPage
import net.s4v3.backend.cv.publiccv.internal.PublicCvLimits
import net.s4v3.backend.cv.publiccv.internal.WindowLimiter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import kotlin.math.max

/**
 * The public CV page and the owner's preview, both without a token. Every state that is not served answers the same 404.
 * Logs only status, duration and the first 8 characters of the ETag: never a handle, an address, a ticket or content.
 */
@RestController
@Tag(name = "Public CV", description = "The caller's own public CV page: handle, visibility settings, preview")
internal class PublicCvPageController(
    private val service: PublicCvService,
    private val limits: PublicCvLimits,
) {

    private val log = LoggerFactory.getLogger(PublicCvPageController::class.java)

    @GetMapping("/api/public/cv/{handle}")
    @Operation(
        summary = "Read a public CV page",
        description = "No token needed. Answers the page as HTML, 304 for a matching `If-None-Match`, or one uniform 404 " +
            "`{\"error\":\"not_found\"}` for every handle that is not served. Limits per client IP, for 404 answers and per handle.",
        responses = [
            ApiResponse(responseCode = "200", description = "The page (text/html)", content = [Content()]),
            ApiResponse(responseCode = "304", description = "Not modified", content = [Content()]),
            ApiResponse(
                responseCode = "404", description = "`not_found`",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
            ApiResponse(
                responseCode = "429", description = "`rate_limited`, with `Retry-After`",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun page(@PathVariable handle: String, request: HttpServletRequest): ResponseEntity<Any> {
        val started = System.nanoTime()
        val ip = clientIp(request)
        val normal = handle.lowercase()
        val answer = if (!PLAUSIBLE.matches(normal)) {
            // Junk makes no limiter key except the IP's own.
            if (limits.notFoundByIp.tryAcquire(ip)) notFound() else rateLimited(limits.notFoundByIp, ip)
        } else if (!limits.pageByIp.tryAcquire(ip)) {
            rateLimited(limits.pageByIp, ip)
        } else if (!limits.pageByHandle.tryAcquire(normal)) {
            rateLimited(limits.pageByHandle, normal)
        } else {
            val rendered = service.renderPublic(normal)
            if (rendered != null) {
                served(rendered, request, noStore = false)
            } else if (limits.notFoundByIp.tryAcquire(ip)) {
                notFound()
            } else {
                rateLimited(limits.notFoundByIp, ip)
            }
        }
        record(answer, started)
        return answer
    }

    @GetMapping("/api/cv/public/preview/{ticket}")
    @Operation(
        summary = "Open an owner preview once",
        description = "No token needed; the one-use ticket from `POST /api/cv/public/preview` is the credential. Answers the " +
            "page once within 60 seconds, then 404. Never cached, never indexable.",
        responses = [
            ApiResponse(responseCode = "200", description = "The page (text/html)", content = [Content()]),
            ApiResponse(
                responseCode = "404", description = "`not_found`",
                content = [Content(schema = Schema(implementation = PublicCvErrorBody::class))],
            ),
        ],
    )
    fun preview(@PathVariable ticket: String, request: HttpServletRequest): ResponseEntity<Any> {
        val started = System.nanoTime()
        val rendered = if (TICKET.matches(ticket)) service.renderPreview(PreviewTicket(ticket)) else null
        val answer = if (rendered == null) notFound() else served(rendered, request, noStore = true)
        record(answer, started)
        return answer
    }

    private fun served(page: RenderedPage, request: HttpServletRequest, noStore: Boolean): ResponseEntity<Any> {
        val quoted = "\"" + page.etag + "\""
        val robots = if (page.indexable && !noStore) "index, follow" else NOINDEX
        fun builder(status: HttpStatus) = ResponseEntity.status(status)
            .header("Content-Security-Policy", CSP)
            .header("X-Content-Type-Options", "nosniff")
            .header("Referrer-Policy", "no-referrer")
            .header(HttpHeaders.CACHE_CONTROL, if (noStore) "no-store" else "no-cache")
            .header("X-Robots-Tag", robots)
            .header(HttpHeaders.ETAG, quoted)
        if (!noStore && matches(request.getHeader(HttpHeaders.IF_NONE_MATCH), quoted)) {
            return builder(HttpStatus.NOT_MODIFIED).build()
        }
        return builder(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, "text/html; charset=utf-8").body(page.html)
    }

    private fun notFound(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).headers(denyHeaders()).body(mapOf("error" to "not_found"))

    private fun rateLimited(limiter: WindowLimiter, key: String): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(denyHeaders())
            .header("Retry-After", max(1L, limiter.retryAfter(key).toSeconds()).toString())
            .body(mapOf("error" to "rate_limited"))

    private fun denyHeaders() = HttpHeaders().apply {
        set(HttpHeaders.CACHE_CONTROL, "no-store")
        set("X-Robots-Tag", NOINDEX)
        set("Referrer-Policy", "no-referrer")
        set("X-Content-Type-Options", "nosniff")
    }

    private fun record(answer: ResponseEntity<*>, startedNanos: Long) {
        val etag = answer.headers.getFirst(HttpHeaders.ETAG)?.trim('"')?.take(8)
        log.info(
            "Public CV answer status={} ms={}{}", answer.statusCode.value(), (System.nanoTime() - startedNanos) / 1_000_000,
            if (etag == null) "" else " etag=$etag",
        )
    }

    /** True when the `If-None-Match` list holds [quoted] (weak or strong) or `*`. */
    private fun matches(header: String?, quoted: String): Boolean =
        header != null && header.split(',').map { it.trim().removePrefix("W/") }.any { it == quoted || it == "*" }

    /** The first address of `X-Forwarded-For` (set by the edge) when it is usable, else the socket address. */
    private fun clientIp(request: HttpServletRequest): String {
        val first = request.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim()
        return if (!first.isNullOrEmpty() && first.length <= MAX_IP) first else request.remoteAddr.orEmpty()
    }

    private companion object {
        val PLAUSIBLE = Regex("[a-z0-9-]{3,30}")
        val TICKET = Regex("[0-9a-f]{32}")
        const val MAX_IP = 45
        const val NOINDEX = "noindex, nofollow, noarchive, nosnippet"
        const val CSP = "default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; " +
            "form-action 'none'; frame-ancestors 'none'; sandbox"
    }
}
