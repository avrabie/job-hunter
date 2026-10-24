package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.RepositoryCatalog
import net.s4v3.backend.profile.RepositoryListing
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Checks the username, picks the provider by its value and caches successful listings for five minutes per
 * provider and username (lower-cased). Failures are not cached. No cache holds anything but public listings.
 */
@Component
internal class DefaultRepositoryCatalog(providers: List<RepositoryProvider>) : RepositoryCatalog {

    private val byProvider: Map<CodeProvider, RepositoryProvider>

    private data class CacheKey(val provider: CodeProvider, val username: String)

    private class Cached(val listing: RepositoryListing.Found, val expiresAt: Long)

    private val cache = ConcurrentHashMap<CacheKey, Cached>()

    init {
        require(providers.map { it.provider }.toSet().size == providers.size) { "one repository provider per value" }
        byProvider = providers.associateBy { it.provider }
    }

    override fun list(provider: CodeProvider, username: String): RepositoryListing {
        if (!isValidUsername(provider, username)) return RepositoryListing.InvalidUsername

        val key = CacheKey(provider, username.lowercase())
        cache[key]?.let { hit ->
            if (System.nanoTime() < hit.expiresAt) return hit.listing
            cache.remove(key, hit)
        }

        val source = byProvider[provider] ?: return RepositoryListing.Unavailable
        val answer = source.list(username)
        if (answer is RepositoryListing.Found) store(key, answer)
        return answer
    }

    private fun store(key: CacheKey, listing: RepositoryListing.Found) {
        if (cache.size >= MAX_ENTRIES) cache.entries.removeIf { System.nanoTime() >= it.value.expiresAt }
        cache[key] = Cached(listing, System.nanoTime() + TimeUnit.MINUTES.toNanos(TTL_MINUTES))
    }

    private companion object {
        const val TTL_MINUTES = 5L
        const val MAX_ENTRIES = 1000
    }
}
