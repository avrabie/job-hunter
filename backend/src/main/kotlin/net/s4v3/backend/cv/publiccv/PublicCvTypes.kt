package net.s4v3.backend.cv.publiccv

import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.CvTemplate
import java.time.Instant

/**
 * What the owner made public. Every flag defaults to off and [sections] to empty, so only the full name is public until
 * something is ticked. Preferences are not part of this object and are never printed. Personal data: never log it.
 */
data class PublicCvSettings(
    val template: CvTemplate = CvTemplate.CLASSIC,
    val language: CvLanguage = CvLanguage.EN,
    val sections: List<CvSectionKind> = emptyList(),
    val headline: Boolean = false,
    val analysis: Boolean = false,
    val workAuthorisation: Boolean = false,
    val location: Boolean = false,
    val contact: PublicCvContact = PublicCvContact(),
    val indexable: Boolean = false,
)

/** The contact items that may be public. Each is off by default. */
data class PublicCvContact(
    val email: Boolean = false,
    val phone: Boolean = false,
    val website: Boolean = false,
    val linkedin: Boolean = false,
    val codeAccounts: Boolean = false,
)

/** The owner's page as the owner sees it: the handle, the switches and when the handle may change again. */
data class PublicCvState(
    val handle: String,
    val published: Boolean,
    val locked: Boolean,
    val settings: PublicCvSettings,
    val handleChangeableAt: Instant?,
    val updatedAt: Instant,
)
