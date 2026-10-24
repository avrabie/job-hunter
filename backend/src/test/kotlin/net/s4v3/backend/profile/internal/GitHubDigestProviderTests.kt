package net.s4v3.backend.profile.internal

import com.sun.net.httpserver.HttpServer
import net.s4v3.backend.profile.DigestResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubDigestProviderTests {

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    private val executor = Executors.newCachedThreadPool()
    private val base = "http://localhost:${server.address.port}"
    private val routes = ConcurrentHashMap<String, Route>()
    private val seenUris = CopyOnWriteArrayList<String>()
    private val seenHeaders = ConcurrentHashMap<String, Map<String, String?>>()

    private data class Route(
        val status: Int,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val delayMs: Long = 0,
    )

    init {
        server.createContext("/") { ex ->
            val uri = ex.requestURI.toString()
            seenUris += uri
            seenHeaders[uri] = mapOf(
                "accept" to ex.requestHeaders.getFirst("Accept"),
                "userAgent" to ex.requestHeaders.getFirst("User-Agent"),
                "version" to ex.requestHeaders.getFirst("X-GitHub-Api-Version"),
            )
            val route = routes[ex.requestURI.rawPath] ?: Route(404, """{"message":"Not Found"}""")
            if (route.delayMs > 0) Thread.sleep(route.delayMs)
            route.headers.forEach { (name, value) -> ex.responseHeaders.add(name, value) }
            val bytes = route.body.toByteArray()
            ex.sendResponseHeaders(route.status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.executor = executor
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun provider(read: Duration = Duration.ofSeconds(2), treeBytes: Int = 8 * 1024 * 1024) =
        GitHubDigestProvider(
            RepositoriesSettings(
                github = RepositoriesSettings.GitHub(
                    baseUrl = base,
                    rawBaseUrl = "$base/raw",
                    readTimeout = read,
                ),
                digest = RepositoriesSettings.Digest(maxTreeBodyBytes = treeBytes),
            ),
        )

    private fun blob(path: String) = path to "blob"

    private fun dir(path: String) = path to "tree"

    /** A tree answer; [entries] are (path, type) pairs in the order the provider lists them. */
    private fun treeJson(vararg entries: Pair<String, String>, truncated: Boolean = false): String {
        val nodes = entries.joinToString(",") { (path, type) -> """{"path":"$path","type":"$type","sha":"x"}""" }
        return """{"sha":"abc","truncated":$truncated,"tree":[$nodes]}"""
    }

    private fun tree(vararg entries: Pair<String, String>, truncated: Boolean = false) {
        routes[TREE] = Route(200, treeJson(*entries, truncated = truncated))
    }

    private fun raw(path: String, body: String, status: Int = 200, delayMs: Long = 0) {
        routes["/raw/octocat/hello-world/HEAD/$path"] = Route(status, body, delayMs = delayMs)
    }

    private fun found(answer: DigestResult) = assertIs<DigestResult.Found>(answer).digest

    @Test
    fun `the README and the manifests are read and cut`() {
        tree(blob("src/Main.kt"), blob("README.md"), blob("build.gradle.kts"), blob("sub/package.json"))
        raw("README.md", "r".repeat(25 * 1024))
        raw("build.gradle.kts", "g".repeat(30 * 1024))
        raw("sub/package.json", "{}")

        val digest = found(provider().digest("octocat/hello-world"))

        assertEquals("octocat/hello-world", digest.fullName)
        assertEquals("r".repeat(20 * 1024), digest.readme)
        assertEquals(listOf("build.gradle.kts", "sub/package.json"), digest.files.keys.toList())
        assertEquals("g".repeat(12 * 1024), digest.files["build.gradle.kts"])
        assertEquals("{}", digest.files["sub/package.json"])
        assertEquals(listOf("src/Main.kt", "README.md", "build.gradle.kts", "sub/package.json"), digest.paths)
        assertFalse(digest.truncated)
    }

    @Test
    fun `at most 8 manifests are read, shallowest first`() {
        tree(
            blob("a/b/package.json"),
            blob("a/pom.xml"),
            blob("build.gradle.kts"),
            blob("settings.gradle.kts"),
            blob("Cargo.toml"),
            blob("go.mod"),
            blob("Gemfile"),
            blob("composer.json"),
            blob("Dockerfile"),
        )
        listOf("a/pom.xml", "build.gradle.kts", "settings.gradle.kts", "Cargo.toml", "go.mod", "Gemfile", "composer.json", "Dockerfile", "a/b/package.json")
            .forEach { raw(it, "x") }

        val digest = found(provider().digest("octocat/hello-world"))

        assertEquals(
            listOf("build.gradle.kts", "settings.gradle.kts", "Cargo.toml", "go.mod", "Gemfile", "composer.json", "Dockerfile", "a/pom.xml"),
            digest.files.keys.toList(),
        )
        assertFalse(seenUris.any { it.endsWith("/a/b/package.json") })
    }

    @Test
    fun `a repository without a README reads no README and has no readme text`() {
        tree(blob("docs/README.md"), blob("build.gradle.kts"))
        raw("build.gradle.kts", "plugins {}")

        val digest = found(provider().digest("octocat/hello-world"))

        assertNull(digest.readme)
        assertEquals(listOf("build.gradle.kts"), digest.files.keys.toList())
        assertFalse(seenUris.any { it.endsWith("/HEAD/README.md") || it.endsWith("/HEAD/docs/README.md") })
    }

    @Test
    fun `the tree keeps directories and files to depth 3 and reports the cut`() {
        tree(dir("a"), dir("a/b"), dir("a/b/c"), blob("a/b/c.txt"), blob("a/b/c/deep.txt"))

        val digest = found(provider().digest("octocat/hello-world"))

        assertEquals(listOf("a", "a/b", "a/b/c", "a/b/c.txt"), digest.paths)
        assertFalse(digest.truncated)
    }

    @Test
    fun `more than 400 paths are cut to 400 and flagged`() {
        tree(*(1..450).map { blob("f$it.txt") }.toTypedArray())

        val digest = found(provider().digest("octocat/hello-world"))

        assertEquals(400, digest.paths.size)
        assertEquals("f1.txt", digest.paths.first())
        assertTrue(digest.truncated)
    }

    @Test
    fun `a tree the provider reports as truncated is flagged and the digest still succeeds`() {
        tree(blob("README.md"), truncated = true)
        raw("README.md", "# Hello")

        val digest = found(provider().digest("octocat/hello-world"))

        assertTrue(digest.truncated)
        assertEquals("# Hello", digest.readme)
    }

    @Test
    fun `404 means the repository is not found`() {
        routes[TREE] = Route(404, """{"message":"Not Found"}""")
        assertEquals(DigestResult.NotFound, provider().digest("octocat/hello-world"))
    }

    @Test
    fun `403 with an exhausted rate limit is rate limited`() {
        routes[TREE] = Route(403, "{}", headers = mapOf("x-ratelimit-remaining" to "0"))
        assertEquals(DigestResult.RateLimited, provider().digest("octocat/hello-world"))
    }

    @Test
    fun `403 without an exhausted rate limit is unavailable`() {
        routes[TREE] = Route(403, "{}", headers = mapOf("x-ratelimit-remaining" to "12"))
        assertEquals(DigestResult.Unavailable, provider().digest("octocat/hello-world"))
    }

    @Test
    fun `429 is rate limited`() {
        routes[TREE] = Route(429, "{}")
        assertEquals(DigestResult.RateLimited, provider().digest("octocat/hello-world"))
    }

    @Test
    fun `5xx is unavailable`() {
        routes[TREE] = Route(503, "{}")
        assertEquals(DigestResult.Unavailable, provider().digest("octocat/hello-world"))
    }

    @Test
    fun `a redirect is not followed and is unavailable`() {
        routes[TREE] = Route(302, "", headers = mapOf("Location" to "http://localhost:1/elsewhere"))
        assertEquals(DigestResult.Unavailable, provider().digest("octocat/hello-world"))
    }

    @Test
    fun `a timeout on the tree is unavailable`() {
        routes[TREE] = Route(200, treeJson(blob("README.md")), delayMs = 1500)
        assertEquals(DigestResult.Unavailable, provider(read = Duration.ofMillis(300)).digest("octocat/hello-world"))
    }

    @Test
    fun `an unparseable tree, a tree without a list and a tree above the size cap are unavailable`() {
        routes[TREE] = Route(200, "{not json")
        assertEquals(DigestResult.Unavailable, provider().digest("octocat/hello-world"))

        routes[TREE] = Route(200, """{"tree":{"path":"README.md"}}""")
        assertEquals(DigestResult.Unavailable, provider().digest("octocat/hello-world"))

        tree(blob("README.md"))
        assertEquals(DigestResult.Unavailable, provider(treeBytes = 16).digest("octocat/hello-world"))
    }

    @Test
    fun `a README download that fails is skipped and the digest still succeeds`() {
        tree(blob("README.md"), blob("build.gradle.kts"), blob("package.json"))
        raw("README.md", "boom", status = 500)
        raw("package.json", "{}")

        val digest = found(provider().digest("octocat/hello-world"))

        assertNull(digest.readme)
        assertEquals(mapOf("package.json" to "{}"), digest.files)
    }

    @Test
    fun `a manifest download that times out is skipped and the digest still succeeds`() {
        tree(blob("README.md"), blob("pom.xml"), blob("package.json"))
        raw("README.md", "# Hello")
        raw("pom.xml", "<project/>", delayMs = 1500)
        raw("package.json", "{}")

        val digest = found(provider(read = Duration.ofMillis(300)).digest("octocat/hello-world"))

        assertEquals("# Hello", digest.readme)
        assertEquals(mapOf("package.json" to "{}"), digest.files)
    }

    @Test
    fun `the requests are the tree call and the raw files, with the pinned headers and encoded segments`() {
        tree(blob("README.md"), blob("sub dir/package.json"))
        raw("README.md", "# Hello")
        raw("sub dir/package.json", "{}")

        provider().digest("octocat/hello-world")

        assertEquals(
            listOf(
                "/repos/octocat/hello-world/git/trees/HEAD?recursive=1",
                "/raw/octocat/hello-world/HEAD/README.md",
                "/raw/octocat/hello-world/HEAD/sub%20dir/package.json",
            ),
            seenUris.toList(),
        )
        val treeHeaders = seenHeaders.getValue("/repos/octocat/hello-world/git/trees/HEAD?recursive=1")
        assertEquals("application/vnd.github+json", treeHeaders["accept"])
        assertEquals("2022-11-28", treeHeaders["version"])
        assertEquals("JobHunter/0.1 (+https://s4v3.net)", treeHeaders["userAgent"])
        assertEquals("JobHunter/0.1 (+https://s4v3.net)", seenHeaders.getValue("/raw/octocat/hello-world/HEAD/README.md")["userAgent"])
    }

    private companion object {
        const val TREE = "/repos/octocat/hello-world/git/trees/HEAD"
    }
}
