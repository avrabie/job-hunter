package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.Repository
import net.s4v3.backend.profile.RepositoryListing
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
import java.time.Instant

/**
 * Public repositories of a GitHub user through the REST API, without credentials. The address comes from
 * configuration; the username is already checked and is one path segment. Logs the outcome and duration only.
 */
@Component
internal class GitHubRepositoryProvider(private val settings: RepositoriesSettings) : RepositoryProvider {

    override val provider = CodeProvider.GITHUB

    private val log = LoggerFactory.getLogger(GitHubRepositoryProvider::class.java)
    private val json = JsonMapper.builder().build()
    private val github = settings.github
    private val base = github.baseUrl.trimEnd('/')

    private val client = HttpClient.newBuilder()
        .connectTimeout(github.connectTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun list(username: String): RepositoryListing {
        val started = System.nanoTime()
        val answer = try {
            fetch(username)
        } catch (e: IOException) {
            RepositoryListing.Unavailable
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            RepositoryListing.Unavailable
        }
        log.info("repository listing {} in {} ms", answer::class.simpleName, (System.nanoTime() - started) / 1_000_000)
        return answer
    }

    private fun fetch(username: String): RepositoryListing {
        val segment = URLEncoder.encode(username, StandardCharsets.UTF_8)
        val uri = URI.create("$base/users/$segment/repos?type=owner&sort=pushed&per_page=100")
        val request = HttpRequest.newBuilder(uri)
            .timeout(github.readTimeout)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", settings.userAgent)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        return response.body().use { stream -> answer(response.statusCode(), response.headers(), stream) }
    }

    private fun answer(status: Int, headers: HttpHeaders, stream: InputStream): RepositoryListing = when (status) {
        200 -> readRepositories(stream)
        404 -> RepositoryListing.NotFound
        429 -> RepositoryListing.RateLimited
        403 -> if (headers.firstValue("x-ratelimit-remaining").orElse(null) == "0") {
            RepositoryListing.RateLimited
        } else {
            RepositoryListing.Unavailable
        }
        else -> RepositoryListing.Unavailable
    }

    private fun readRepositories(stream: InputStream): RepositoryListing {
        val bytes = stream.readNBytes(github.maxBodyBytes + 1)
        if (bytes.size > github.maxBodyBytes) return RepositoryListing.Unavailable
        val root: JsonNode = try {
            json.readTree(bytes)
        } catch (e: JacksonException) {
            return RepositoryListing.Unavailable
        }
        if (!root.isArray) return RepositoryListing.Unavailable
        val repositories = root.mapNotNull { toRepository(it) }
        return RepositoryListing.Found(repositories)
    }

    /** Null for forks, archived repositories and entries without a name or an https address. */
    private fun toRepository(node: JsonNode): Repository? {
        if (node.path("fork").asBoolean(false) || node.path("archived").asBoolean(false)) return null
        val fullName = text(node, "full_name") ?: return null
        val name = text(node, "name") ?: return null
        val url = text(node, "html_url")?.takeIf { it.startsWith("https://") } ?: return null
        val stars = node.path("stargazers_count").takeIf { it.isNumber }?.asInt()
        return Repository(
            provider = CodeProvider.GITHUB,
            fullName = fullName,
            name = name,
            url = url,
            description = text(node, "description"),
            language = text(node, "language"),
            topics = node.path("topics").filter { it.isString }.map { it.asString() },
            stars = stars?.takeIf { it >= 0 },
            pushedAt = text(node, "pushed_at")?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    private fun text(node: JsonNode, field: String): String? =
        node.path(field).takeIf { it.isString }?.asString()?.trim()?.takeIf { it.isNotEmpty() }
}
