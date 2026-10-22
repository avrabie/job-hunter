package net.s4v3.backend

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A local stand-in for the Greenhouse board API, serving synthetic boards written for the tests. */
class FakeGreenhouse : AutoCloseable {

    data class Job(
        val id: Int,
        val title: String,
        val location: String,
        val content: String = "&lt;p&gt;Build and run services&lt;/p&gt;",
        val company: String = "Acme",
    )

    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    val baseUrl = "http://localhost:${server.address.port}"

    val boards = ConcurrentHashMap<String, List<Job>>()
    val requests = CopyOnWriteArrayList<String>()
    val userAgents = CopyOnWriteArrayList<String>()

    @Volatile var robotsStatus = 200
    @Volatile var robotsBody = "User-agent: *\nDisallow: /embed/\n"
    @Volatile var ignoreEtag = false
    @Volatile var listStatus: Int? = null
    @Volatile var listBody: String? = null
    @Volatile var listContentType = "application/json"
    @Volatile var onList: (String) -> Unit = {}
    val detailStatus = ConcurrentHashMap<Int, Int>()
    @Volatile var detailBody: String? = null

    init {
        server.createContext("/") { ex -> handle(ex) }
        server.start()
    }

    fun etag(board: String): String = "W/\"" + boards[board].orEmpty().hashCode() + "\""

    fun listRequests(board: String) = requests.count { it == "GET /v1/boards/$board/jobs" }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        requests += "${ex.requestMethod} $path"
        ex.requestHeaders.getFirst("User-Agent")?.let { userAgents += it }
        when {
            path == "/robots.txt" -> send(ex, robotsStatus, "text/plain", robotsBody)
            Regex("/v1/boards/([^/]+)/jobs").matches(path) -> list(ex, path.split('/')[3])
            Regex("/v1/boards/([^/]+)/jobs/(\\d+)").matches(path) -> detail(ex, path.split('/')[3], path.split('/')[5].toInt())
            else -> send(ex, 404, "text/plain", "not found")
        }
    }

    private fun list(ex: HttpExchange, board: String) {
        onList(board)
        listStatus?.let { return send(ex, it, "application/json", listBody ?: "{}") }
        val jobs = boards[board] ?: return send(ex, 404, "application/json", """{"error":"Job not found"}""")
        listBody?.let { return send(ex, 200, listContentType, it) }
        val etag = etag(board)
        if (!ignoreEtag && ex.requestHeaders.getFirst("If-None-Match") == etag) {
            ex.responseHeaders.add("ETag", etag)
            ex.sendResponseHeaders(304, -1)
            ex.close()
            return
        }
        ex.responseHeaders.add("ETag", etag)
        val items = jobs.joinToString(",") {
            """{"id":${it.id},"title":"${it.title}","location":{"name":"${it.location}"},"company_name":"${it.company}","absolute_url":"https://example.org/${it.id}"}"""
        }
        send(ex, 200, "application/json", """{"jobs":[$items],"meta":{"total":${jobs.size}}}""")
    }

    private fun detail(ex: HttpExchange, board: String, id: Int) {
        detailStatus[id]?.let { return send(ex, it, "application/json", detailBody ?: "{}") }
        val job = boards[board]?.firstOrNull { it.id == id } ?: return send(ex, 404, "application/json", "{}")
        send(
            ex, 200, "application/json",
            """{"id":${job.id},"title":"${job.title}","location":{"name":"${job.location}"},"company_name":"${job.company}",
                "absolute_url":"https://example.org/${job.id}","content":"${job.content}"}""".trimIndent(),
        )
    }

    private fun send(ex: HttpExchange, status: Int, type: String, body: String) {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}
