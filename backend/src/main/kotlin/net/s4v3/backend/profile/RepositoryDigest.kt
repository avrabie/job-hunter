package net.s4v3.backend.profile

/**
 * What an analysis reads from one repository: the README text, the paths of the file tree and the text of a few
 * build or dependency files by path. [truncated] is true when the tree was cut. Repository text is untrusted data.
 */
data class RepositoryDigest(
    val provider: CodeProvider,
    val fullName: String,
    val readme: String?,
    val paths: List<String>,
    val files: Map<String, String>,
    val truncated: Boolean,
)
