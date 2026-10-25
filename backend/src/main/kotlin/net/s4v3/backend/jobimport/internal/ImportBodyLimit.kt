package net.s4v3.backend.jobimport.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Answers 413 before the body of an import call is read when it is declared larger than [MAX_BYTES], and 411 when no size is
 * declared (chunked), so a huge JSON string is never materialised. Runs after the security chain.
 */
@Component
internal class ImportBodyLimit : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method == "POST" && (request.requestURI == "/api/job-imports" || request.requestURI == "/api/job-imports/preview"))

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val declared = request.contentLengthLong
        val chunked = declared < 0 && request.getHeader("Transfer-Encoding") != null
        if (declared > MAX_BYTES || chunked) {
            response.status = if (chunked) 411 else 413
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":"too_large","message":"The request is too large."}""")
            return
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val MAX_BYTES = 256 * 1024L
    }
}
