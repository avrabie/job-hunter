package net.s4v3.backend.cv.export.internal

import net.s4v3.backend.cv.export.ContactVisibility
import net.s4v3.backend.cv.export.CvDocument
import net.s4v3.backend.cv.export.CvEntry
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvLink
import net.s4v3.backend.cv.export.CvPhoto
import net.s4v3.backend.cv.export.CvSection
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.EntrySection
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.GroupedTagSection
import net.s4v3.backend.cv.export.LineSection
import net.s4v3.backend.cv.export.ProseSection
import net.s4v3.backend.cv.export.SkillGroup
import net.s4v3.backend.cv.export.SkillGroups
import net.s4v3.backend.cv.export.TagGroup
import net.s4v3.backend.cv.export.TagSection
import net.s4v3.backend.profile.CodeAccount
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.Education
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.Location
import net.s4v3.backend.profile.OpenSourceProject
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.Publication
import net.s4v3.backend.profile.Skill

/**
 * Turns a [Profile] and the export options into a [CvDocument]: which sections, in which order, with which entries.
 * Preferences, skill sources and every field not in the `cv-export` spec are never copied.
 */
internal object CvBuilder {

    private val defaultOrder = listOf(
        CvSectionKind.PROFILE,
        CvSectionKind.EXPERIENCE,
        CvSectionKind.OPEN_SOURCE,
        CvSectionKind.EDUCATION,
        CvSectionKind.SKILLS,
        CvSectionKind.LANGUAGES,
        CvSectionKind.PUBLICATIONS,
    )

    /**
     * [photo] is the already processed photo (see `PhotoProcessor`); the builder stays pure and never reads files.
     * It is used only for the PROFESSIONAL template.
     */
    fun build(profile: Profile, options: ExportOptions, photo: ByteArray? = null): CvDocument {
        val language = options.language
        val order = (options.sections ?: defaultOrder).distinct()
        return CvDocument(
            language = language,
            name = clean(profile.contact.fullName).orEmpty(),
            headline = if (options.contact.headline) clean(profile.contact.headline) else null,
            contactLines = contactLines(profile, options.contact),
            facts = facts(profile, options),
            sections = order.mapNotNull { section(it, profile, options) },
            photo = if (options.template == CvTemplate.PROFESSIONAL) photo?.let { CvPhoto(it) } else null,
        )
    }

    private fun contactLines(profile: Profile, visibility: ContactVisibility): List<CvLink> = buildList {
        val contact = profile.contact
        if (visibility.email) clean(contact.email)?.let { add(CvLink(it)) }
        if (visibility.phone) clean(contact.phone)?.let { add(CvLink(it)) }
        if (visibility.location) location(profile.location)?.let { add(CvLink(it)) }
        if (visibility.website) clean(contact.website)?.let { add(CvLink(it, it)) }
        if (visibility.linkedin) clean(contact.linkedin)?.let { add(CvLink(it, it)) }
        if (visibility.codeAccounts) profile.codeAccounts.forEach { add(codeAccountLink(it)) }
    }

    private fun facts(profile: Profile, options: ExportOptions): List<String> {
        val authorisation = profile.workAuthorisation ?: return emptyList()
        if (!options.includeWorkAuthorisation) return emptyList()
        val language = options.language
        return listOf("${Labels.workAuthorisationLabel(language)}: ${Labels.workAuthorisation(authorisation, language)}")
    }

    private fun section(kind: CvSectionKind, profile: Profile, options: ExportOptions): CvSection? {
        val language = options.language
        val title = Labels.heading(kind, language)
        return when (kind) {
            CvSectionKind.PROFILE ->
                clean(profile.summary)?.let { ProseSection(title, listOf(it)) }

            CvSectionKind.EXPERIENCE ->
                entries(title, profile.experience.map { experience(it, language) })

            CvSectionKind.OPEN_SOURCE ->
                if (options.includeOpenSource) {
                    entries(title, profile.openSourceProjects.map { project(it, options) })
                } else {
                    null
                }

            CvSectionKind.EDUCATION ->
                entries(title, profile.education.map { education(it, language) })

            CvSectionKind.SKILLS ->
                if (options.template == CvTemplate.PROFESSIONAL) {
                    groupedSkills(title, profile, language)
                } else {
                    profile.skills.mapNotNull { skillTag(it, language) }
                        .ifEmpty { null }
                        ?.let { TagSection(title, it) }
                }

            CvSectionKind.LANGUAGES ->
                profile.languages
                    .map { "${Labels.languageName(it.code, language)} – ${Labels.languageLevel(it.level, language)}" }
                    .ifEmpty { null }
                    ?.let { LineSection(title, it) }

            CvSectionKind.PUBLICATIONS ->
                entries(title, profile.publications.map { publication(it) })
        }
    }

    private fun entries(title: String, entries: List<CvEntry>): EntrySection? =
        entries.ifEmpty { null }?.let { EntrySection(title, it) }

    private fun experience(experience: Experience, language: CvLanguage) = CvEntry(
        heading = experience.employer,
        subheading = clean(experience.title),
        period = Labels.period(experience.start, experience.end, language),
        place = clean(experience.location),
        bullets = experience.achievements.mapNotNull { clean(it) },
        tags = experience.technologies.mapNotNull { clean(it) },
    )

    /** Description, then the user's note and, when asked, the analysis summary; tags are the analysed technologies. */
    private fun project(project: OpenSourceProject, options: ExportOptions): CvEntry {
        val analysis = if (options.includeAnalysis) project.analysis else null
        return CvEntry(
            heading = project.name,
            subheading = listOfNotNull(
                clean(project.language),
                project.stars?.let { Labels.stars(it, options.language) },
            ).joinToString(" · ").ifEmpty { null },
            link = CvLink(project.fullName, project.url),
            text = clean(project.description),
            bullets = listOfNotNull(clean(project.note), analysis?.let { clean(it.summary) }),
            tags = analysis?.techStack?.mapNotNull { clean(it.name) }?.distinct().orEmpty(),
        )
    }

    private fun education(education: Education, language: CvLanguage) = CvEntry(
        heading = education.institution,
        subheading = listOfNotNull(clean(education.degree), clean(education.field)).joinToString(", ").ifEmpty { null },
        period = Labels.period(education.start, education.end, language),
    )

    private fun publication(publication: Publication) = CvEntry(
        heading = publication.title,
        subheading = clean(publication.authors),
        period = publication.year?.toString(),
        place = clean(publication.venue),
        link = clean(publication.url)?.let { CvLink(it, it) },
    )

    /** Each skill goes under exactly one group, in the order typed; groups print in [SkillGroup] order, empty ones are dropped. */
    private fun groupedSkills(title: String, profile: Profile, language: CvLanguage): GroupedTagSection? {
        val byGroup = profile.skills.mapNotNull { skill -> clean(skill.name)?.let { SkillGroups.group(it) to skillTag(skill, language)!! } }
            .groupBy({ it.first }, { it.second })
        return SkillGroup.entries.mapNotNull { group ->
            byGroup[group]?.let { TagGroup(Labels.skillGroup(group, language), it) }
        }.ifEmpty { null }?.let { GroupedTagSection(title, it) }
    }

    private fun skillTag(skill: Skill, language: CvLanguage): String? {
        val name = clean(skill.name) ?: return null
        return skill.level?.let { "$name (${Labels.skillLevel(it, language)})" } ?: name
    }

    private fun codeAccountLink(account: CodeAccount): CvLink = when (account.provider) {
        CodeProvider.GITHUB -> CvLink("github.com/${account.username}", "https://github.com/${account.username}")
    }

    private fun location(location: Location?): String? =
        listOfNotNull(clean(location?.city), clean(location?.canton)).joinToString(", ").ifEmpty { null }

    /** The value as the user wrote it, or null when it is blank. */
    private fun clean(value: String?): String? = value?.takeIf { it.isNotBlank() }
}
