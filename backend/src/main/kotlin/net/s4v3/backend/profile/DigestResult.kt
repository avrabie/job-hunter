package net.s4v3.backend.profile

/** The answer of [RepositoryDigestSource.digest]. Only [Found] carries data; the other kinds are fixed failure reasons. */
sealed interface DigestResult {
    data class Found(val digest: RepositoryDigest) : DigestResult
    data object InvalidName : DigestResult
    data object NotFound : DigestResult
    data object RateLimited : DigestResult
    data object Unavailable : DigestResult
}
