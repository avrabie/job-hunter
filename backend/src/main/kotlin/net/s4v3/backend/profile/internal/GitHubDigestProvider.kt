package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.DigestResult
import net.s4v3.backend.profile.RepositoryDigest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

private val manifestNames = setOf(
    "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "pom.xml", "package.json",
    "requirements.txt", "pyproject.toml", "Cargo.toml", "go.mod", "Gemfile", "composer.json", "Dockerfile",
    "docker-compose.yml",
)

/**
 * A repository digest through the GitHub REST API (one tree call) and the raw content host (one request per file),
 * without credentials and without cloning. Addresses come from configuration; owner and repo are already checked and
 * are encoded here as path segments. Logs the outcome and duration only.
 */
@Component
internal class GitHubDigestProvider(private val settings: RepositoriesSettings) : RepositoryDigestProvider {

    override val provider = CodeProvider.GITHUB

    private val log = LoggerFactory.getLogger(GitHubDigestProvider::class.java)
    private val json = JsonMapper.builder().build()
    private val github = settings.github
    private val limits = settings.digest
    private val apiBase = github.baseUrl.trimEnd('/')
    private val rawBase = github.rawBaseUrl.trimEnd('/')

    private val client = HttpClient.newBuilder()
        .connectTimeout(github.connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private class Entry(val path: String, val isBlob: Boolean)

    private class Tree(val entries: List<Entry>, val truncated: Boolean)

    private sealed interface TreeAnswer {
        class Read(val tree: Tree) : TreeAnswer
        class Failed(val result: DigestResult) : TreeAnswer
    }

    override fun digest(fullName: String): DigestResult {
        val started = System.nanoTime()
        val answer = try {
            fetch(fullName)
        } catch (e: IOException) {
            DigestResult.Unavailable
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            DigestResult.Unavailable
        }
        log.info("repository digest {} in {} ms", answer::class.simpleName, (System.nanoTime() - started) / 1_000_000)
        return answer
    }

    private fun fetch(fullName: String): DigestResult {
        val (owner, repo) = fullName.split('/').map { encode(it) }
        val tree = when (val answer = fetchTree(owner, repo)) {
            is TreeAnswer.Failed -> return answer.result
            is TreeAnswer.Read -> answer.tree
        }

        val inDepth = tree.entries.filter { depth(it.path) <= limits.maxTreeDepth }
        val kept = inDepth.take(limits.maxTreePaths)
        val truncated = tree.truncated || kept.size < inDepth.size

        val readme = kept.firstOrNull { it.isBlob && depth(it.path) == 1 && fileName(it.path).startsWith("README", ignoreCase = true) }
        val readmeText = readme?.let { download(owner, repo, it.path, limits.maxReadmeBytes) }

        val files = LinkedHashMap<String, String>()
        kept.filter { it.isBlob && fileName(it.path) in manifestNames }
            .sortedBy { depth(it.path) }
            .take(limits.maxManifests)
            .forEach { manifest ->
                download(owner, repo, manifest.path, limits.maxManifestBytes)?.let { files[manifest.path] = it }
            }

        return DigestResult.Found(
            RepositoryDigest(
                provider = CodeProvider.GITHUB,
                fullName = fullName,
                readme = readmeText,
                paths = kept.map { it.path },
                files = files,
                truncated = truncated,
            ),
        )
    }

    private fun fetchTree(owner: String, repo: String): TreeAnswer {
        val uri = URI.create("$apiBase/repos/$owner/$repo/git/trees/HEAD?recursive=1")
        val request = HttpRequest.newBuilder(uri)
            .timeout(github.readTimeout)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", settings.userAgent)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        return response.body().use { stream ->
            if (response.statusCode() == 200) {
                readTree(stream)
            } else {
                TreeAnswer.Failed(failure(response.statusCode(), response.headers()))
            }
        }
    }

    private fun readTree(stream: InputStream): TreeAnswer {
        val bytes = stream.readNBytes(limits.maxTreeBodyBytes + 1)
        if (bytes.size > limits.maxTreeBodyBytes) return TreeAnswer.Failed(DigestResult.Unavailable)
        val root: JsonNode = try {
            json.readTree(bytes)
        } catch (e: JacksonException) {
            return TreeAnswer.Failed(DigestResult.Unavailable)
        }
        val nodes = root.path("tree")
        if (!nodes.isArray) return TreeAnswer.Failed(DigestResult.Unavailable)
        val entries = nodes.mapNotNull { node ->
            val path = text(node, "path") ?: return@mapNotNull null
            when (text(node, "type")) {
                "blob" -> Entry(path, isBlob = true)
                "tree" -> Entry(path, isBlob = false)
                else -> null
            }
        }
        return TreeAnswer.Read(Tree(entries, truncated = root.path("truncated").asBoolean(false)))
    }

    /** The text of one raw file, cut at [maxBytes]; null when the download fails in any way, so the file is skipped. */
    private fun download(owner: String, repo: String, path: String, maxBytes: Int): String? {
        val encodedPath = path.split('/').joinToString("/") { encode(it) }
        val request = HttpRequest.newBuilder(URI.create("$rawBase/$owner/$repo/HEAD/$encodedPath"))
            .timeout(github.readTimeout)
            .header("User-Agent", settings.userAgent)
            .GET()
            .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { stream ->
                if (response.statusCode() == 200) String(stream.readNBytes(maxBytes), StandardCharsets.UTF_8) else null
            }
        } catch (e: IOException) {
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private fun failure(status: Int, headers: HttpHeaders): DigestResult = when (status) {
        404 -> DigestResult.NotFound
        429 -> DigestResult.RateLimited
        403 -> if (headers.firstValue("x-ratelimit-remaining").orElse(null) == "0") {
            DigestResult.RateLimited
        } else {
            DigestResult.Unavailable
        }
        else -> DigestResult.Unavailable
    }

    private fun encode(segment: String): String =
        URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")

    private fun depth(path: String): Int = path.count { it == '/' } + 1

    private fun fileName(path: String): String = path.substringAfterLast('/')

    private fun text(node: JsonNode, field: String): String? =
        node.path(field).takeIf { it.isString }?.asString()?.takeIf { it.isNotEmpty() }
}
