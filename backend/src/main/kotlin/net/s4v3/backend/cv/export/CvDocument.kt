package net.s4v3.backend.cv.export

/**
 * The CV as text, ready for a renderer: labels, months and levels are already in [CvLanguage]; the renderers only lay
 * the content out. Every string is the user's own text or a fixed label. Personal data: never log it.
 */
data class CvDocument(
    val language: CvLanguage,
    val name: String,
    val headline: String?,
    val contactLines: List<CvLink>,
    val facts: List<String>,
    val sections: List<CvSection>,
    val photo: CvPhoto? = null,
)

/** A piece of text that is a link when [url] is set. Renderers link only http and https addresses. */
data class CvLink(val text: String, val url: String? = null)

/** A titled part of the CV. */
sealed interface CvSection {
    val title: String
}

/** Free text, such as the summary. */
data class ProseSection(override val title: String, val paragraphs: List<String>) : CvSection

/** Dated entries, such as experience or education. */
data class EntrySection(override val title: String, val entries: List<CvEntry>) : CvSection

/** Short words shown as a comma list, such as skills. */
data class TagSection(override val title: String, val tags: List<String>) : CvSection

/** Skills in named groups (Professional template only), printed as a two-column table. Empty groups are not present. */
data class GroupedTagSection(override val title: String, val groups: List<TagGroup>) : CvSection

/** One group of a [GroupedTagSection]: the group name and its skills in the order the user typed them. */
data class TagGroup(val name: String, val tags: List<String>)

/** One line per item, such as languages. */
data class LineSection(override val title: String, val lines: List<String>) : CvSection

/** One dated entry. Empty parts are null or empty and are not printed. */
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

/** Turns a [CvDocument] into bytes in one format. Each renderer is pure: the same document gives the same bytes. */
interface CvRenderer {
    fun render(document: CvDocument, template: CvTemplate): ByteArray
}
