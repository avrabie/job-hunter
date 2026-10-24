package net.s4v3.backend.profile.internal

import com.sun.net.httpserver.HttpServer
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.RepositoryListing
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GitHubRepositoryProviderTests {

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply { start() }
    private val seenUris = CopyOnWriteArrayList<String>()
    private val seenHeaders = CopyOnWriteArrayList<Map<String, String?>>()

    @AfterEach
    fun stop() = server.stop(0)

    private fun serve(
        status: Int,
        body: String = "[]",
        headers: Map<String, String> = emptyMap(),
        delayMs: Long = 0,
        path: String = "/users/octocat/repos",
    ) {
        server.createContext(path) { ex ->
            seenUris += ex.requestURI.toString()
            seenHeaders += mapOf(
                "accept" to ex.requestHeaders.getFirst("Accept"),
                "userAgent" to ex.requestHeaders.getFirst("User-Agent"),
                "version" to ex.requestHeaders.getFirst("X-GitHub-Api-Version"),
            )
            if (delayMs > 0) Thread.sleep(delayMs)
            headers.forEach { (name, value) -> ex.responseHeaders.add(name, value) }
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
    }

    private fun provider(read: Duration = Duration.ofSeconds(2), maxBytes: Int = 2 * 1024 * 1024) =
        GitHubRepositoryProvider(
            RepositoriesSettings(
                github = RepositoriesSettings.GitHub(
                    baseUrl = "http://localhost:${server.address.port}",
                    readTimeout = read,
                    maxBodyBytes = maxBytes,
                ),
            ),
        )

    private val listing = """
        [
          {"full_name":"octocat/hello-world","name":"hello-world","html_url":"https://github.com/octocat/hello-world",
           "description":"My first repository","language":"Kotlin","topics":["demo","kotlin"],"stargazers_count":42,
           "pushed_at":"2026-09-30T12:00:00Z","fork":false,"archived":false,"private":false,"extra":{"ignored":true}},
          {"full_name":"octocat/forked","name":"forked","html_url":"https://github.com/octocat/forked",
           "description":null,"language":null,"topics":[],"stargazers_count":1,"pushed_at":null,"fork":true,"archived":false},
          {"full_name":"octocat/old","name":"old","html_url":"https://github.com/octocat/old",
           "description":null,"language":null,"topics":[],"stargazers_count":2,"pushed_at":null,"fork":false,"archived":true}
        ]
    """.trimIndent()

    @Test
    fun `forks and archived repositories are dropped and the rest keeps every field`() {
        serve(200, listing)

        val found = assertIs<RepositoryListing.Found>(provider().list("octocat"))
        assertEquals(1, found.repositories.size)
        val hello = found.repositories.single()
        assertEquals(CodeProvider.GITHUB, hello.provider)
        assertEquals("octocat/hello-world", hello.fullName)
        assertEquals("hello-world", hello.name)
        assertEquals("https://github.com/octocat/hello-world", hello.url)
        assertEquals("My first repository", hello.description)
        assertEquals("Kotlin", hello.language)
        assertEquals(listOf("demo", "kotlin"), hello.topics)
        assertEquals(42, hello.stars)
        assertEquals(Instant.parse("2026-09-30T12:00:00Z"), hello.pushedAt)
    }

    @Test
    fun `null description, language and push time are allowed`() {
        serve(200, """[{"full_name":"octocat/bare","name":"bare","html_url":"https://github.com/octocat/bare",
            "description":null,"language":null,"topics":[],"stargazers_count":0,"pushed_at":null,"fork":false,"archived":false}]""")

        val bare = assertIs<RepositoryListing.Found>(provider().list("octocat")).repositories.single()
        assertNull(bare.description)
        assertNull(bare.language)
        assertNull(bare.pushedAt)
    }

    @Test
    fun `the request is one page of the owner's repositories with the pinned headers`() {
        serve(200)

        provider().list("octocat")

        assertEquals(listOf("/users/octocat/repos?type=owner&sort=pushed&per_page=100"), seenUris)
        val headers = seenHeaders.single()
        assertEquals("application/vnd.github+json", headers["accept"])
        assertEquals("2022-11-28", headers["version"])
        assertEquals("JobHunter/0.1 (+https://s4v3.net)", headers["userAgent"])
    }

    @Test
    fun `404 means the account does not exist`() {
        serve(404, """{"message":"Not Found"}""")
        assertEquals(RepositoryListing.NotFound, provider().list("octocat"))
    }

    @Test
    fun `403 with an exhausted rate limit is rate limited`() {
        serve(403, """{"message":"API rate limit exceeded"}""", headers = mapOf("x-ratelimit-remaining" to "0"))
        assertEquals(RepositoryListing.RateLimited, provider().list("octocat"))
    }

    @Test
    fun `403 without an exhausted rate limit is unavailable`() {
        serve(403, """{"message":"Forbidden"}""", headers = mapOf("x-ratelimit-remaining" to "12"))
        assertEquals(RepositoryListing.Unavailable, provider().list("octocat"))
    }

    @Test
    fun `429 is rate limited`() {
        serve(429, """{"message":"Too Many Requests"}""")
        assertEquals(RepositoryListing.RateLimited, provider().list("octocat"))
    }

    @Test
    fun `5xx is unavailable`() {
        serve(503, """{"message":"down"}""")
        assertEquals(RepositoryListing.Unavailable, provider().list("octocat"))
    }

    @Test
    fun `a redirect is not followed and is unavailable`() {
        serve(302, "", headers = mapOf("Location" to "http://localhost:1/elsewhere"))
        assertEquals(RepositoryListing.Unavailable, provider().list("octocat"))
    }

    @Test
    fun `a timeout is unavailable`() {
        serve(200, listing, delayMs = 1500)
        assertEquals(RepositoryListing.Unavailable, provider(read = Duration.ofMillis(300)).list("octocat"))
    }

    @Test
    fun `an unparseable or non-list body is unavailable`() {
        serve(200, "{not json")
        assertEquals(RepositoryListing.Unavailable, provider().list("octocat"))

        serve(200, """{"full_name":"octocat/x"}""", path = "/users/other/repos")
        assertEquals(RepositoryListing.Unavailable, provider().list("other"))
    }

    @Test
    fun `a body above the size cap is unavailable`() {
        serve(200, listing)
        assertEquals(RepositoryListing.Unavailable, provider(maxBytes = 64).list("octocat"))
    }
}
