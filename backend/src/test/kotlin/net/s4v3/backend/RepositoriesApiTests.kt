package net.s4v3.backend

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertTrue

@ApiTest
class RepositoriesApiTests(@Autowired private val mvc: MockMvc) {

    companion object {
        private val github = FakeGitHub()

        @JvmStatic
        @DynamicPropertySource
        fun githubBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("jobhunter.repositories.github.base-url") { "http://localhost:${github.port}" }
        }
    }

    private val issuer get() = SharedMockIssuer.instance
    private fun user() = "repo-user-" + UUID.randomUUID()
    private fun bearer(sub: String) = "Bearer ${issuer.token(sub)}"

    private fun list(sub: String, provider: String, username: String) =
        mvc.perform(
            get("/api/profile/repositories").param("provider", provider).param("username", username)
                .header("Authorization", bearer(sub)),
        )

    private val octocatRepos = """
        [
          {"full_name":"octocat/hello-world","name":"hello-world","html_url":"https://github.com/octocat/hello-world",
           "description":"My first repository","language":"Kotlin","topics":["demo","kotlin"],"stargazers_count":42,
           "pushed_at":"2026-09-30T12:00:00Z","fork":false,"archived":false},
          {"full_name":"octocat/forked","name":"forked","html_url":"https://github.com/octocat/forked",
           "description":null,"language":null,"topics":[],"stargazers_count":1,"pushed_at":null,"fork":true,"archived":false},
          {"full_name":"octocat/old","name":"old","html_url":"https://github.com/octocat/old",
           "description":null,"language":null,"topics":[],"stargazers_count":2,"pushed_at":null,"fork":false,"archived":true}
        ]
    """.trimIndent()

    @Test
    fun `listing returns the public repositories with the listed fields`() {
        github.answer("/users/octocat/repos", 200, octocatRepos)
        val sub = user()

        list(sub, "GITHUB", "octocat").andExpect(status().isOk)
            .andExpect(jsonPath("$.repositories.length()").value(1))
            .andExpect(jsonPath("$.repositories[0].provider").value("GITHUB"))
            .andExpect(jsonPath("$.repositories[0].fullName").value("octocat/hello-world"))
            .andExpect(jsonPath("$.repositories[0].url").value("https://github.com/octocat/hello-world"))
            .andExpect(jsonPath("$.repositories[0].description").value("My first repository"))
            .andExpect(jsonPath("$.repositories[0].topics[1]").value("kotlin"))
            .andExpect(jsonPath("$.repositories[0].stars").value(42))
            .andExpect(jsonPath("$.repositories[0].pushedAt").value("2026-09-30T12:00:00Z"))
    }

    @Test
    fun `an unknown account answers 404 account_not_found`() {
        github.answer("/users/ghost-account/repos", 404, """{"message":"Not Found"}""")

        list(user(), "GITHUB", "ghost-account").andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("account_not_found"))
    }

    @Test
    fun `an exhausted provider limit answers 429 provider_rate_limited`() {
        github.answer("/users/busy-account/repos", 403, """{"message":"limit"}""", "x-ratelimit-remaining" to "0")

        list(user(), "GITHUB", "busy-account").andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error").value("provider_rate_limited"))
    }

    @Test
    fun `a failing provider answers 502 provider_unavailable`() {
        github.answer("/users/down-account/repos", 503, """{"message":"down"}""")

        list(user(), "GITHUB", "down-account").andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.error").value("provider_unavailable"))
    }

    @Test
    fun `invalid input is refused before any outside call`() {
        github.requests.clear()
        val sub = user()

        list(sub, "GITHUB", "../users").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_username"))
        list(sub, "NOPE", "octocat").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("unknown_provider"))

        assertTrue(github.requests.isEmpty(), "the provider was called ${github.requests.size} times")
    }

    @Test
    fun `anonymous calls get 401`() {
        mvc.perform(get("/api/profile/repositories").param("provider", "GITHUB").param("username", "octocat"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `listing leaves the stored profile and its version unchanged`() {
        val sub = user()
        mvc.perform(
            put("/api/profile").header("Authorization", bearer(sub)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":0,"profile":{"summary":"Backend engineer"}}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        github.answer("/users/steady-account/repos", 200, octocatRepos)

        list(sub, "GITHUB", "steady-account").andExpect(status().isOk)

        mvc.perform(get("/api/profile").header("Authorization", bearer(sub)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.profile.summary").value("Backend engineer"))
    }

    /** A local stand-in for the GitHub API; answers are set per request path. */
    private class FakeGitHub : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        val port get() = server.address.port

        private data class Answer(val status: Int, val body: String, val header: Pair<String, String>?)

        private val answers = ConcurrentHashMap<String, Answer>()
        val requests = CopyOnWriteArrayList<String>()

        init {
            server.createContext("/") { ex ->
                requests += ex.requestURI.path
                val answer = answers[ex.requestURI.path] ?: Answer(404, """{"message":"Not Found"}""", null)
                answer.header?.let { ex.responseHeaders.add(it.first, it.second) }
                val bytes = answer.body.toByteArray()
                ex.responseHeaders.add("Content-Type", "application/json")
                ex.sendResponseHeaders(answer.status, bytes.size.toLong())
                ex.responseBody.use { it.write(bytes) }
            }
            server.start()
        }

        fun answer(path: String, status: Int, body: String, header: Pair<String, String>? = null) {
            answers[path] = Answer(status, body, header)
        }

        override fun close() = server.stop(0)
    }
}
