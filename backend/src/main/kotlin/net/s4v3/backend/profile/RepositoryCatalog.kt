package net.s4v3.backend.profile

import java.time.Instant

/**
 * Public repositories of a code account, read from the provider's public API. Reads nothing from and writes nothing
 * to any profile. Usernames are personal data: never log them.
 */
interface RepositoryCatalog {

    fun list(provider: CodeProvider, username: String): RepositoryListing
}

/** The answer of [RepositoryCatalog.list]. Only [Found] carries data; the other kinds are fixed failure reasons. */
sealed interface RepositoryListing {
    data class Found(val repositories: List<Repository>) : RepositoryListing
    data object InvalidUsername : RepositoryListing
    data object NotFound : RepositoryListing
    data object RateLimited : RepositoryListing
    data object Unavailable : RepositoryListing
}

/** A public repository as the provider describes it. [pushedAt] is null when the provider does not say. */
data class Repository(
    val provider: CodeProvider,
    val fullName: String,
    val name: String,
    val url: String,
    val description: String? = null,
    val language: String? = null,
    val topics: List<String> = emptyList(),
    val stars: Int? = null,
    val pushedAt: Instant? = null,
)
