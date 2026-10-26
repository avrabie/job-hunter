package net.s4v3.backend

import net.s4v3.backend.FakeHttpServer.Reply
import net.s4v3.backend.FakeHttpServer.Request
import java.util.concurrent.CopyOnWriteArrayList

/** A local stand-in for SwissDevJobs, serving a synthetic job list at `/api/jobsLight` and a feed at `/rss`. */
class FakeSwissDevJobs : AutoCloseable {

    /** [inFeed] false leaves the job out of the feed, like a partner ad. */
    data class Job(
        val slug: String,
        val name: String = "Backend Engineer",
        val company: String = "Acme",
        val city: String? = "Zug",
        val workplace: String? = "hybrid",
        val from: Int? = 80000,
        val to: Int? = 120000,
        val paused: Boolean = false,
        val description: String = "<p>Build and run services</p>",
        val inFeed: Boolean = true,
    )

    private val server = FakeHttpServer()
    val baseUrl = server.baseUrl

    @Volatile var jobs: List<Job> = emptyList()
    val requests = CopyOnWriteArrayList<String>()
    val userAgents = CopyOnWriteArrayList<String>()
    val feedAccepts = CopyOnWriteArrayList<String>()

    @Volatile var robotsStatus = 200
    @Volatile var robotsBody = "User-agent: *\nDisallow: /static/\n"
    @Volatile var ignoreEtag = false
    @Volatile var listStatus: Int? = null
    @Volatile var listBody: String? = null
    @Volatile var listContentType = "application/json"
    @Volatile var feedStatus: Int? = null
    @Volatile var feedBody: String? = null

    init {
        server.fallback = ::handle
    }

    fun etag(): String = "W/\"" + jobs.hashCode() + "\""

    fun listRequests() = requests.count { it == "GET /api/jobsLight" }

    fun feedRequests() = requests.count { it == "GET /rss" }

    private fun handle(req: Request): Reply {
        requests += "${req.method} ${req.path}"
        req.header("User-Agent")?.let { userAgents += it }
        return when (req.path) {
            "/robots.txt" -> Reply(robotsStatus, robotsBody, "text/plain")
            "/api/jobsLight" -> list(req)
            "/rss" -> feed(req)
            else -> Reply(404, "not found", "text/plain")
        }
    }

    private fun list(req: Request): Reply {
        listStatus?.let { return Reply(it, listBody ?: "[]") }
        listBody?.let { return Reply(200, it, listContentType) }
        val etag = etag()
        if (!ignoreEtag && req.header("If-None-Match") == etag) {
            return Reply(304, contentType = null, headers = mapOf("ETag" to etag))
        }
        val entries = jobs.joinToString(",") {
            fun text(name: String, value: String?) = if (value == null) "" else """"$name":"$value","""
            fun number(name: String, value: Int?) = if (value == null) "" else """"$name":$value,"""
            "{" + text("jobUrl", it.slug) + text("name", it.name) + text("company", it.company) +
                text("actualCity", it.city) + text("cityCategory", it.city) + text("workplace", it.workplace) +
                number("annualSalaryFrom", it.from) + number("annualSalaryTo", it.to) +
                text("redirectJobUrl", "https://apply.example/${it.slug}") + """"isPaused":${it.paused}}"""
        }
        return Reply(200, "[$entries]", headers = mapOf("ETag" to etag))
    }

    private fun feed(req: Request): Reply {
        req.header("Accept")?.let { feedAccepts += it }
        feedStatus?.let { return Reply(it, feedBody ?: "", "application/xml") }
        feedBody?.let { return Reply(200, it, "application/xml") }
        val items = jobs.filter { it.inFeed }.joinToString("\n") {
            """<item><title><![CDATA[${it.name} @ ${it.company}]]></title>
               <link>https://swissdevjobs.ch/jobs/${it.slug}?utm_source=our_rss_feed&amp;utm_medium=our_rss_feed</link>
               <description><![CDATA[${it.description}]]></description></item>"""
        }
        return Reply(200, """<?xml version="1.0" encoding="utf-8"?><rss version="2.0"><channel><title>Fake</title>$items</channel></rss>""", "application/xml")
    }

    override fun close() = server.close()
}
