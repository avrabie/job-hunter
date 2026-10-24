package net.s4v3.backend.cv.export

// STAND-IN for group 2 (document model). The lead deletes this file at merge.

enum class CvTemplate { CLASSIC, MODERN, COMPACT }

enum class CvLanguage { EN, DE }

data class CvLink(val text: String, val url: String? = null)

data class CvEntry(
    val heading: String,
    val subheading: String? = null,
    val period: String? = null,
    val place: String? = null,
    val link: CvLink? = null,
    val text: String? = null,
    val bullets: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

sealed interface CvSection {
    val title: String
}

data class ProseSection(override val title: String, val paragraphs: List<String>) : CvSection

data class EntrySection(override val title: String, val entries: List<CvEntry>) : CvSection

data class TagSection(override val title: String, val tags: List<String>) : CvSection

data class LineSection(override val title: String, val lines: List<String>) : CvSection

data class CvDocument(
    val language: CvLanguage,
    val name: String,
    val headline: String?,
    val contactLines: List<CvLink>,
    val facts: List<String>,
    val sections: List<CvSection>,
)

interface CvRenderer {
    fun render(document: CvDocument, template: CvTemplate): ByteArray
}
