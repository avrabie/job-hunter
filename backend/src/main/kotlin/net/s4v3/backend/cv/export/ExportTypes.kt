package net.s4v3.backend.cv.export

import java.util.UUID

/** The file format of an export, with the content type and file extension it is served under. */
enum class CvFormat(val contentType: String, val extension: String) {
    MARKDOWN("text/markdown; charset=utf-8", "md"),
    HTML("text/html; charset=utf-8", "html"),
    PDF("application/pdf", "pdf"),
}

/** The layout of an export. Markdown has one structure for all templates. [CvTemplate.PROFESSIONAL] adds grouped skills and an optional photo. */
enum class CvTemplate { CLASSIC, MODERN, COMPACT, PROFESSIONAL }

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
    val photoFileId: UUID? = null,
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
