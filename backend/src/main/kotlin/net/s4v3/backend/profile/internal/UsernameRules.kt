package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider

/** Username rules per provider. Stand-in for the group 1 rule; the lead resolves the overlap at merge. */
internal fun isValidUsername(provider: CodeProvider, username: String): Boolean = when (provider) {
    CodeProvider.GITHUB -> gitHubUsername.matches(username)
}

/** 1 to 39 letters, digits or single hyphens, not starting or ending with a hyphen. */
private val gitHubUsername = Regex("[A-Za-z0-9](?:-?[A-Za-z0-9]){0,38}")
