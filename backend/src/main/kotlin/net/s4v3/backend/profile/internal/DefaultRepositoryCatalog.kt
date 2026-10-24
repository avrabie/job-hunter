package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.RepositoryCatalog
import net.s4v3.backend.profile.RepositoryListing
import org.springframework.stereotype.Component

/**
 * Checks the username, picks the provider by its value and caches successful listings for five minutes per
 * provider and username (lower-cased). Failures are not cached. No cache holds anything but public listings.
 */
@Component
internal class DefaultRepositoryCatalog(providers: List<RepositoryProvider>) : RepositoryCatalog {

    private val byProvider: Map<CodeProvider, RepositoryProvider>

    private val cache = ProviderCache<RepositoryListing.Found>(ttlMinutes = TTL_MINUTES, maxEntries = MAX_ENTRIES)

    init {
        require(providers.map { it.provider }.toSet().size == providers.size) { "one repository provider per value" }
        byProvider = providers.associateBy { it.provider }
    }

    override fun list(provider: CodeProvider, username: String): RepositoryListing {
        if (!isValidUsername(provider, username)) return RepositoryListing.InvalidUsername

        cache.get(provider, username)?.let { return it }

        val source = byProvider[provider] ?: return RepositoryListing.Unavailable
        val answer = source.list(username)
        if (answer is RepositoryListing.Found) cache.put(provider, username, answer)
        return answer
    }

    private companion object {
        const val TTL_MINUTES = 5L
        const val MAX_ENTRIES = 1000
    }
}
