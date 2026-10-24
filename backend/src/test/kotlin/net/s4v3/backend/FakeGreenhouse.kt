package net.s4v3.backend

import net.s4v3.backend.FakeHttpServer.Reply
import net.s4v3.backend.FakeHttpServer.Request
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

    private val server = FakeHttpServer()
    val baseUrl = server.baseUrl

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
        server.fallback = ::handle
    }

    fun etag(board: String): String = "W/\"" + boards[board].orEmpty().hashCode() + "\""

    fun listRequests(board: String) = requests.count { it == "GET /v1/boards/$board/jobs" }

    private fun handle(req: Request): Reply {
        val path = req.path
        requests += "${req.method} $path"
        req.header("User-Agent")?.let { userAgents += it }
        return when {
            path == "/robots.txt" -> Reply(robotsStatus, robotsBody, "text/plain")
            Regex("/v1/boards/([^/]+)/jobs").matches(path) -> list(req, path.split('/')[3])
            Regex("/v1/boards/([^/]+)/jobs/(\\d+)").matches(path) -> detail(path.split('/')[3], path.split('/')[5].toInt())
            else -> Reply(404, "not found", "text/plain")
        }
    }

    private fun list(req: Request, board: String): Reply {
        onList(board)
        listStatus?.let { return Reply(it, listBody ?: "{}") }
        val jobs = boards[board] ?: return Reply(404, """{"error":"Job not found"}""")
        listBody?.let { return Reply(200, it, listContentType) }
        val etag = etag(board)
        if (!ignoreEtag && req.header("If-None-Match") == etag) {
            return Reply(304, contentType = null, headers = mapOf("ETag" to etag))
        }
        val items = jobs.joinToString(",") {
            """{"id":${it.id},"title":"${it.title}","location":{"name":"${it.location}"},"company_name":"${it.company}","absolute_url":"https://example.org/${it.id}"}"""
        }
        return Reply(200, """{"jobs":[$items],"meta":{"total":${jobs.size}}}""", headers = mapOf("ETag" to etag))
    }

    private fun detail(board: String, id: Int): Reply {
        detailStatus[id]?.let { return Reply(it, detailBody ?: "{}") }
        val job = boards[board]?.firstOrNull { it.id == id } ?: return Reply(404, "{}")
        return Reply(
            200,
            """{"id":${job.id},"title":"${job.title}","location":{"name":"${job.location}"},"company_name":"${job.company}",
                "absolute_url":"https://example.org/${job.id}","content":"${job.content}"}""".trimIndent(),
        )
    }

    override fun close() = server.close()
}
