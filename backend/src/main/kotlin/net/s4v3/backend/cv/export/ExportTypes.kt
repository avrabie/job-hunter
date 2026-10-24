package net.s4v3.backend.cv.export

/** The file format of an export. */
enum class CvFormat { MARKDOWN, HTML, PDF }

/** The layout of an export. Markdown has one structure for all templates. */
enum class CvTemplate { CLASSIC, MODERN, COMPACT }

/** The language of the labels and wording. The user's own text is never translated. */
enum class CvLanguage { EN, DE }

/** The sections a CV can print, in the default order. */
enum class CvSectionKind { PROFILE, EXPERIENCE, OPEN_SOURCE, EDUCATION, SKILLS, LANGUAGES, PUBLICATIONS }

/**
 * What the caller asked for. [sections] null means all sections in the default order; a list selects and orders them.
 * Personal data: never log it.
 */
data class ExportOptions(
    val format: CvFormat,
    val template: CvTemplate,
    val language: CvLanguage,
    val sections: List<CvSectionKind>?,
    val includeOpenSource: Boolean = true,
    val includeAnalysis: Boolean = false,
    val includeWorkAuthorisation: Boolean = true,
)
