package net.s4v3.backend.profile

import java.time.Instant

// STAND-IN for task 3.1 only: the lead deletes this file at merge, when group 1 (Profile.kt) and group 2 (digest) are in.
// Exactly the Contract's definitions of design.md.

enum class TechCategory { LANGUAGE, FRAMEWORK, LIBRARY, DATABASE, TOOL, PLATFORM, OTHER }

data class Technology(val name: String, val category: TechCategory, val evidencePath: String, val evidence: String)

data class ProjectAnalysis(
    val summary: String,
    val architecture: String,
    val techStack: List<Technology>,
    val files: List<String>,
    val truncated: Boolean,
    val analyzedAt: Instant,
)

data class RepositoryDigest(
    val provider: CodeProvider,
    val fullName: String,
    val readme: String?,
    val paths: List<String>,
    val files: Map<String, String>,
    val truncated: Boolean,
)
