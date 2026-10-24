package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.profile.LanguageLevel
import net.s4v3.backend.profile.SkillLevel
import net.s4v3.backend.profile.WorkAuthorisation
import java.time.YearMonth
import java.util.Locale

/**
 * The fixed words of a CV in each language: headings, the word for a current position, months, levels and work
 * authorisations. German uses Swiss spelling (no `ß`). Nothing here is the user's text.
 */
internal object Labels {

    private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun heading(kind: CvSectionKind, language: CvLanguage): String = when (language) {
        CvLanguage.EN -> when (kind) {
            CvSectionKind.PROFILE -> "Profile"
            CvSectionKind.EXPERIENCE -> "Experience"
            CvSectionKind.OPEN_SOURCE -> "Open source projects"
            CvSectionKind.EDUCATION -> "Education"
            CvSectionKind.SKILLS -> "Skills"
            CvSectionKind.LANGUAGES -> "Languages"
            CvSectionKind.PUBLICATIONS -> "Publications"
        }
        CvLanguage.DE -> when (kind) {
            CvSectionKind.PROFILE -> "Profil"
            CvSectionKind.EXPERIENCE -> "Berufserfahrung"
            CvSectionKind.OPEN_SOURCE -> "Open-Source-Projekte"
            CvSectionKind.EDUCATION -> "Ausbildung"
            CvSectionKind.SKILLS -> "Kenntnisse"
            CvSectionKind.LANGUAGES -> "Sprachen"
            CvSectionKind.PUBLICATIONS -> "Publikationen"
        }
    }

    fun workAuthorisationLabel(language: CvLanguage): String = when (language) {
        CvLanguage.EN -> "Work authorisation"
        CvLanguage.DE -> "Arbeitsbewilligung"
    }

    fun present(language: CvLanguage): String = when (language) {
        CvLanguage.EN -> "Present"
        CvLanguage.DE -> "heute"
    }

    /** EN `Mar 2021`, DE `03.2021`. */
    fun month(value: YearMonth, language: CvLanguage): String = when (language) {
        CvLanguage.EN -> "${monthsEn[value.monthValue - 1]} ${value.year}"
        CvLanguage.DE -> "${value.monthValue.toString().padStart(2, '0')}.${value.year}"
    }

    /**
     * `Mar 2021 – Present`. A missing end reads as present. A missing start with an end prints the end alone; with
     * neither there is no period.
     */
    fun period(start: YearMonth?, end: YearMonth?, language: CvLanguage): String? = when {
        start == null && end == null -> null
        start == null -> month(end!!, language)
        else -> "${month(start, language)} – ${end?.let { month(it, language) } ?: present(language)}"
    }

    fun skillLevel(level: SkillLevel, language: CvLanguage): String = when (language) {
        CvLanguage.EN -> when (level) {
            SkillLevel.BEGINNER -> "Beginner"
            SkillLevel.INTERMEDIATE -> "Intermediate"
            SkillLevel.ADVANCED -> "Advanced"
            SkillLevel.EXPERT -> "Expert"
        }
        CvLanguage.DE -> when (level) {
            SkillLevel.BEGINNER -> "Grundkenntnisse"
            SkillLevel.INTERMEDIATE -> "Gute Kenntnisse"
            SkillLevel.ADVANCED -> "Fortgeschritten"
            SkillLevel.EXPERT -> "Experte"
        }
    }

    fun languageLevel(level: LanguageLevel, language: CvLanguage): String = when (language) {
        CvLanguage.EN -> when (level) {
            LanguageLevel.A1 -> "Basic"
            LanguageLevel.A2 -> "Elementary"
            LanguageLevel.B1 -> "Intermediate"
            LanguageLevel.B2 -> "Upper intermediate"
            LanguageLevel.C1 -> "Advanced"
            LanguageLevel.C2 -> "Proficient"
            LanguageLevel.NATIVE -> "Native"
        }
        CvLanguage.DE -> when (level) {
            LanguageLevel.A1 -> "Grundkenntnisse"
            LanguageLevel.A2 -> "Elementar"
            LanguageLevel.B1 -> "Mittelstufe"
            LanguageLevel.B2 -> "Gute Kenntnisse"
            LanguageLevel.C1 -> "Fortgeschritten"
            LanguageLevel.C2 -> "Fliessend"
            LanguageLevel.NATIVE -> "Muttersprache"
        }
    }

    fun workAuthorisation(value: WorkAuthorisation, language: CvLanguage): String = when (language) {
        CvLanguage.EN -> when (value) {
            WorkAuthorisation.SWISS_CITIZEN -> "Swiss citizen"
            WorkAuthorisation.PERMIT_C -> "Swiss permit C (settled)"
            WorkAuthorisation.PERMIT_B -> "Swiss permit B (residence)"
            WorkAuthorisation.PERMIT_L -> "Swiss permit L (short-term)"
            WorkAuthorisation.PERMIT_G -> "Swiss cross-border permit G"
            WorkAuthorisation.NEEDS_SPONSORSHIP -> "Work permit sponsorship needed"
        }
        CvLanguage.DE -> when (value) {
            WorkAuthorisation.SWISS_CITIZEN -> "Schweizer Staatsbürger/in"
            WorkAuthorisation.PERMIT_C -> "Niederlassungsbewilligung C"
            WorkAuthorisation.PERMIT_B -> "Aufenthaltsbewilligung B"
            WorkAuthorisation.PERMIT_L -> "Kurzaufenthaltsbewilligung L"
            WorkAuthorisation.PERMIT_G -> "Grenzgängerbewilligung G"
            WorkAuthorisation.NEEDS_SPONSORSHIP -> "Arbeitsbewilligung erforderlich"
        }
    }

    /** The language's own name in the CV language, from its ISO code. Falls back to the code. */
    fun languageName(code: String, language: CvLanguage): String {
        val locale = when (language) {
            CvLanguage.EN -> Locale.ENGLISH
            CvLanguage.DE -> Locale.GERMAN
        }
        return Locale.forLanguageTag(code).getDisplayLanguage(locale).ifBlank { code }
    }

    fun stars(count: Int, language: CvLanguage): String = when (language) {
        CvLanguage.EN -> if (count == 1) "1 star" else "$count stars"
        CvLanguage.DE -> if (count == 1) "1 Stern" else "$count Sterne"
    }
}
