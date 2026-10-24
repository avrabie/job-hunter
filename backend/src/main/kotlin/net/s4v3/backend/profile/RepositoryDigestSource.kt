package net.s4v3.backend.profile

/**
 * Reads one public repository from its provider for analysis: the file tree, the README and a few build files. Reads
 * nothing from and writes nothing to any profile, and clones nothing. Repository names and text are never logged.
 */
interface RepositoryDigestSource {

    fun digest(provider: CodeProvider, fullName: String): DigestResult
}
