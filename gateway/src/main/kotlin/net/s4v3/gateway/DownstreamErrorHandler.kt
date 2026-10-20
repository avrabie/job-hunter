package net.s4v3.gateway

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import java.io.IOException

/**
 * Spring Cloud Gateway answers 500 when a downstream service refuses or drops the connection (only response timeouts
 * become 504). A service that is down or being replaced is a bad gateway, not a gateway bug, so those errors become a
 * 502 with a fixed body. The address of the service is never put in the response; the log line carries the exception
 * class only, because the exception message names the address.
 */
@Component
@Order(-2) // before the framework's default error handler
class DownstreamErrorHandler : WebExceptionHandler {

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        if (exchange.response.isCommitted || !isDownstreamConnectionFailure(ex)) return Mono.error(ex)

        log.warn("Downstream connection failed: {}", ex.javaClass.simpleName)
        val response = exchange.response
        response.statusCode = HttpStatus.BAD_GATEWAY
        response.headers.contentType = MediaType.APPLICATION_JSON
        val factory: DataBufferFactory = response.bufferFactory()
        return response.writeWith(Mono.just(factory.wrap("""{"error":"bad_gateway"}""".toByteArray())))
    }

    private fun isDownstreamConnectionFailure(ex: Throwable): Boolean =
        generateSequence(ex) { it.cause }.take(8).any { it is IOException }

    private companion object {
        val log = LoggerFactory.getLogger(DownstreamErrorHandler::class.java)
    }
}
