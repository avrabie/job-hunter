package net.s4v3.backend.files.internal

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView

/**
 * Answers 507 before the body of `POST /api/files` is read when its declared size would leave less than `min-free-bytes`
 * free on the files volume. Runs after the security chain, so an anonymous call still gets 401 first.
 */
@Component
internal class FreeSpaceGuard(
    private val storage: FileStorage,
    private val settings: FilesSettings,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method == "POST" && request.requestURI == UPLOAD_PATH)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val declared = request.contentLengthLong.coerceAtLeast(0)
        if (storage.usableSpace() - settings.minFreeBytes < declared) {
            response.status = NO_ROOM_STATUS
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(NO_ROOM_BODY)
            return
        }
        chain.doFilter(request, response)
    }

    private companion object {
        const val UPLOAD_PATH = "/api/files"
        const val NO_ROOM_STATUS = 507
        const val NO_ROOM_BODY = """{"error":"invalid_file","message":"${FilesMessages.NO_ROOM}"}"""
    }
}

/**
 * The multipart limit (10 GB, set in application.yaml) is enforced by Tomcat before any controller runs. For the files
 * upload it answers with the files message; every other path is left to the existing handlers, unchanged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class FilesUploadLimitResolver : HandlerExceptionResolver {

    override fun resolveException(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any?,
        ex: Exception,
    ): ModelAndView? {
        if (ex !is MaxUploadSizeExceededException || request.requestURI != UPLOAD_PATH) return null
        response.status = 413
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"error":"invalid_file","message":"${FilesMessages.TOO_LARGE_OTHER}"}""")
        return ModelAndView()
    }

    private companion object {
        const val UPLOAD_PATH = "/api/files"
    }
}
