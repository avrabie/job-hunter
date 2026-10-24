package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.DigestResult
import net.s4v3.backend.profile.RepositoryDigestSource
import net.s4v3.backend.profile.isValidFullName
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Checks `owner/repo`, picks the provider by its value and caches found digests for ten minutes per provider and full
 * name (lower-cased). Failures are not cached. Dot segments are refused so a name cannot change the address of a request.
 */
@Component
internal class DefaultRepositoryDigestSource(providers: List<RepositoryDigestProvider>) : RepositoryDigestSource {

    private val byProvider: Map<CodeProvider, RepositoryDigestProvider>

    private data class CacheKey(val provider: CodeProvider, val fullName: String)

    private class Cached(val found: DigestResult.Found, val expiresAt: Long)

    private val cache = ConcurrentHashMap<CacheKey, Cached>()

    init {
        require(providers.map { it.provider }.toSet().size == providers.size) { "one repository digest provider per value" }
        byProvider = providers.associateBy { it.provider }
    }

    override fun digest(provider: CodeProvider, fullName: String): DigestResult {
        if (!isValidFullName(fullName)) return DigestResult.InvalidName

        val key = CacheKey(provider, fullName.lowercase())
        cache[key]?.let { hit ->
            if (System.nanoTime() < hit.expiresAt) return hit.found
            cache.remove(key, hit)
        }

        val source = byProvider[provider] ?: return DigestResult.Unavailable
        val answer = source.digest(fullName)
        if (answer is DigestResult.Found) store(key, answer)
        return answer
    }

    private fun store(key: CacheKey, found: DigestResult.Found) {
        if (cache.size >= MAX_ENTRIES) cache.entries.removeIf { System.nanoTime() >= it.value.expiresAt }
        cache[key] = Cached(found, System.nanoTime() + TimeUnit.MINUTES.toNanos(TTL_MINUTES))
    }

    private companion object {
        const val TTL_MINUTES = 10L
        const val MAX_ENTRIES = 1000
    }
}
