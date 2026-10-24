package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.DigestResult
import net.s4v3.backend.profile.RepositoryDigestSource
import net.s4v3.backend.profile.isValidFullName
import org.springframework.stereotype.Component

/**
 * Checks `owner/repo`, picks the provider by its value and caches found digests for ten minutes per provider and full
 * name (lower-cased). Failures are not cached. Dot segments are refused so a name cannot change the address of a request.
 */
@Component
internal class DefaultRepositoryDigestSource(providers: List<RepositoryDigestProvider>) : RepositoryDigestSource {

    private val byProvider: Map<CodeProvider, RepositoryDigestProvider>

    private val cache = ProviderCache<DigestResult.Found>(ttlMinutes = TTL_MINUTES, maxEntries = MAX_ENTRIES)

    init {
        require(providers.map { it.provider }.toSet().size == providers.size) { "one repository digest provider per value" }
        byProvider = providers.associateBy { it.provider }
    }

    override fun digest(provider: CodeProvider, fullName: String): DigestResult {
        if (!isValidFullName(fullName)) return DigestResult.InvalidName

        cache.get(provider, fullName)?.let { return it }

        val source = byProvider[provider] ?: return DigestResult.Unavailable
        val answer = source.digest(fullName)
        if (answer is DigestResult.Found) cache.put(provider, fullName, answer)
        return answer
    }

    private companion object {
        const val TTL_MINUTES = 10L
        const val MAX_ENTRIES = 1000
    }
}
