package net.s4v3.backend

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A local HTTP server for tests. It listens on [host] at a random port until [close]. Each request is
 * answered by the route registered for its exact path, or by [fallback]; every request is logged in [requests].
 */
class FakeHttpServer(host: String = "localhost") : AutoCloseable {

    /** A request as received. [finishedAt] is the `System.nanoTime()` at which the reply was sent. */
    class Request(
        val method: String,
        /** The path without the query, as sent. */
        val path: String,
        /** The path with the query, as sent. */
        val uri: String,
        private val headers: Headers,
    ) {
        val startedAt: Long = System.nanoTime()

        @Volatile
        var finishedAt: Long = 0

        fun header(name: String): String? = headers.getFirst(name)
    }

    /** A reply. A null [contentType] sends no Content-Type header; 204 and 304 send no body. */
    class Reply(
        val status: Int = 200,
        val body: String = "",
        val contentType: String? = "application/json",
        val headers: Map<String, String> = emptyMap(),
    )

    val requests = CopyOnWriteArrayList<Request>()

    /** Answers requests whose path has no route. */
    @Volatile
    var fallback: (Request) -> Reply = { Reply(404, """{"message":"Not Found"}""") }

    private val routes = ConcurrentHashMap<String, (Request) -> Reply>()
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val http: HttpServer = HttpServer.create(InetSocketAddress(host, 0), 0)

    val port: Int = http.address.port

    val baseUrl = "http://$host:$port"

    init {
        http.createContext("/") { ex ->
            val request = Request(ex.requestMethod, ex.requestURI.rawPath, ex.requestURI.toString(), ex.requestHeaders)
            requests += request
            send(ex, (routes[request.path] ?: fallback)(request))
            request.finishedAt = System.nanoTime()
        }
        http.executor = executor
        http.start()
    }

    /** Answers requests for [path] (exact match, no query) with the result of [handler]. Replaces an earlier route. */
    fun on(path: String, handler: (Request) -> Reply) {
        routes[path] = handler
    }

    private fun send(ex: HttpExchange, reply: Reply) {
        reply.contentType?.let { ex.responseHeaders.add("Content-Type", it) }
        reply.headers.forEach { (name, value) -> ex.responseHeaders.add(name, value) }
        if (reply.status == 204 || reply.status == 304) {
            ex.sendResponseHeaders(reply.status, -1)
            ex.close()
            return
        }
        val bytes = reply.body.toByteArray()
        ex.sendResponseHeaders(reply.status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    override fun close() {
        http.stop(0)
        executor.shutdownNow()
    }
}
