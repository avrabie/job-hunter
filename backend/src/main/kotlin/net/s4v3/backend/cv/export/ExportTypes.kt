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
    val contact: ContactVisibility = ContactVisibility.ALL,
)

/**
 * Which contact items the CV prints. An item that is off appears nowhere in the document. The full name is always
 * printed; work authorisation is controlled by [ExportOptions.includeWorkAuthorisation].
 */
data class ContactVisibility(
    val email: Boolean = true,
    val phone: Boolean = true,
    val website: Boolean = true,
    val linkedin: Boolean = true,
    val codeAccounts: Boolean = true,
    val location: Boolean = true,
    val headline: Boolean = true,
) {
    companion object {
        val ALL = ContactVisibility()
        val NONE = ContactVisibility(
            email = false,
            phone = false,
            website = false,
            linkedin = false,
            codeAccounts = false,
            location = false,
            headline = false,
        )
    }
}
