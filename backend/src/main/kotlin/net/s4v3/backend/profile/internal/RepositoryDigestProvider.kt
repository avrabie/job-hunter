package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.DigestResult

/**
 * One code-hosting provider for digests. Implementations are picked by [provider] and get only full names that passed
 * the name rule of [DefaultRepositoryDigestSource]. Answers use the fixed kinds of [DigestResult].
 */
internal interface RepositoryDigestProvider {

    val provider: CodeProvider

    fun digest(fullName: String): DigestResult
}
