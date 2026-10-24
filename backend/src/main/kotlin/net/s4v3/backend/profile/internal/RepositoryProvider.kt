package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.RepositoryListing

/**
 * One code-hosting provider. Implementations are picked by [provider] and get only usernames that passed
 * [isValidUsername]. Answers use the fixed kinds of [RepositoryListing]; nothing else leaves the provider.
 */
internal interface RepositoryProvider {

    val provider: CodeProvider

    fun list(username: String): RepositoryListing
}
