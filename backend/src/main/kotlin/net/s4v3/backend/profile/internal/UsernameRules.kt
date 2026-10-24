package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider

private val githubUsername = Regex("[A-Za-z0-9]+(-[A-Za-z0-9]+)*")

/**
 * Whether [username] follows the rule of [provider]. Checked before any outside call, so a value that could change the
 * address of a request never leaves the service. A new provider adds its rule here.
 */
internal fun isValidUsername(provider: CodeProvider, username: String): Boolean =
    when (provider) {
        CodeProvider.GITHUB -> username.length in 1..39 && githubUsername.matches(username)
    }
