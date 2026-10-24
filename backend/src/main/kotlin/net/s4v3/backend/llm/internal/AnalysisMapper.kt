package net.s4v3.backend.llm.internal

import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.profile.ProjectAnalysis
import net.s4v3.backend.profile.RepositoryDigest
import net.s4v3.backend.profile.TechCategory
import net.s4v3.backend.profile.Technology
import java.time.Instant

/**
 * Turns the model's [ProjectAnalysisDraft] into a [ProjectAnalysis] and checks the evidence. A technology is kept only if the
 * path it cites is a path of the digest's tree (or the README), so an instruction inside the repository cannot add a technology
 * the files do not contain. An empty summary makes the whole call fail as unusable.
 */
internal object AnalysisMapper {

    fun toAnalysis(draft: ProjectAnalysisDraft, digest: RepositoryDigest, analyzedAt: Instant): ProjectAnalysis {
        val summary = text(draft.summary)?.let { cut(it, SUMMARY_MAX) } ?: throw LlmFailure(LlmFailureKind.FAILED)
        val readme = readmePath(digest)
        val paths = digest.paths.toSet() + listOfNotNull(readme)
        return ProjectAnalysis(
            summary = summary,
            architecture = cut(text(draft.architecture) ?: "", ARCHITECTURE_MAX),
            techStack = technologies(draft.techStack, paths),
            files = (listOfNotNull(readme) + digest.files.keys).distinct().filter { it.length <= PATH_MAX }.take(FILES_MAX),
            truncated = digest.truncated,
            analyzedAt = analyzedAt,
        )
    }

    /** The README is the first file at the root of the tree whose name starts with `README`; null when there is none. */
    fun readmePath(digest: RepositoryDigest): String? =
        if (digest.readme == null) null
        else digest.paths.firstOrNull { !it.contains('/') && it.startsWith("README", ignoreCase = true) }

    /**
     * Drops a technology whose name is not 1 to 60 characters or whose evidence path is not in the tree, keeps the first spelling
     * of a case-insensitive duplicate, makes an unknown category `OTHER` and keeps at most 20.
     */
    private fun technologies(drafts: List<TechnologyDraft>, paths: Set<String>): List<Technology> {
        val seen = HashSet<String>()
        return drafts.mapNotNull { technology(it, paths) }
            .filter { seen.add(it.name.lowercase()) }
            .take(TECHNOLOGIES_MAX)
    }

    private fun technology(draft: TechnologyDraft, paths: Set<String>): Technology? {
        val name = text(draft.name)?.takeIf { it.length <= NAME_MAX } ?: return null
        val evidencePath = text(draft.evidencePath)?.takeIf { it in paths && it.length <= PATH_MAX } ?: return null
        return Technology(
            name = name,
            category = enumOrOther(draft.category),
            evidencePath = evidencePath,
            evidence = cut(text(draft.evidence) ?: "", EVIDENCE_MAX),
        )
    }

    /** Cuts at the last sentence end in the second half of the limit when there is one, otherwise at the limit. */
    private fun cut(value: String, max: Int): String {
        if (value.length <= max) return value
        val head = value.take(max)
        val sentenceEnd = head.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (sentenceEnd >= max / 2) head.take(sentenceEnd + 1) else head.trimEnd()
    }

    private fun enumOrOther(value: String?): TechCategory {
        val wanted = text(value)?.uppercase() ?: return TechCategory.OTHER
        return TechCategory.entries.firstOrNull { it.name == wanted } ?: TechCategory.OTHER
    }

    private fun text(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private const val SUMMARY_MAX = 1500
    private const val ARCHITECTURE_MAX = 2500
    private const val EVIDENCE_MAX = 300
    private const val NAME_MAX = 60
    private const val PATH_MAX = 300
    private const val TECHNOLOGIES_MAX = 20
    private const val FILES_MAX = 9
}
