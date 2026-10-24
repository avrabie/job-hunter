package net.s4v3.backend

import net.s4v3.backend.cv.export.CvFormat
import net.s4v3.backend.cv.export.CvLanguage
import net.s4v3.backend.cv.export.CvSectionKind
import net.s4v3.backend.cv.export.CvTemplate
import net.s4v3.backend.cv.export.ExportOptions
import net.s4v3.backend.cv.export.GroupedTagSection
import net.s4v3.backend.cv.export.TagSection
import net.s4v3.backend.cv.export.internal.CvBuilder
import net.s4v3.backend.profile.Contact
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.Skill
import net.s4v3.backend.profile.SkillLevel
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CvBuilderProfessionalTests {

    private val profile = Profile(
        contact = Contact(fullName = "Ada Example"),
        skills = listOf(
            Skill("Java", SkillLevel.EXPERT), Skill("Kafka"), Skill("PostgreSQL"), Skill("Basket weaving"), Skill("Kotlin"),
        ),
    )

    private fun options(template: CvTemplate, language: CvLanguage = CvLanguage.EN) =
        ExportOptions(CvFormat.PDF, template, language, listOf(CvSectionKind.SKILLS))

    @Test
    fun `professional groups skills in print order and keeps the typed order inside a group`() {
        val section = CvBuilder.build(profile, options(CvTemplate.PROFESSIONAL)).sections.single()
        assertIs<GroupedTagSection>(section)
        assertEquals(
            listOf("Programming languages", "Messaging", "Databases", "Other"),
            section.groups.map { it.name },
        )
        assertEquals(listOf("Java (Expert)", "Kotlin"), section.groups[0].tags)
        assertEquals(listOf("Basket weaving"), section.groups.last().tags)
    }

    @Test
    fun `group names follow the language`() {
        val section = CvBuilder.build(profile, options(CvTemplate.PROFESSIONAL, CvLanguage.DE)).sections.single()
        assertEquals(
            listOf("Programmiersprachen", "Messaging", "Datenbanken", "Weitere"),
            (section as GroupedTagSection).groups.map { it.name },
        )
    }

    @Test
    fun `other templates keep the flat list`() {
        for (t in listOf(CvTemplate.CLASSIC, CvTemplate.MODERN, CvTemplate.COMPACT)) {
            val section = CvBuilder.build(profile, options(t)).sections.single()
            assertIs<TagSection>(section)
            assertEquals(listOf("Java (Expert)", "Kafka", "PostgreSQL", "Basket weaving", "Kotlin"), section.tags)
        }
    }

    @Test
    fun `the photo is carried only by professional`() {
        val bytes = byteArrayOf(1, 2, 3)
        assertContentEquals(bytes, CvBuilder.build(profile, options(CvTemplate.PROFESSIONAL), bytes).photo!!.bytes)
        assertNull(CvBuilder.build(profile, options(CvTemplate.CLASSIC), bytes).photo)
        assertNull(CvBuilder.build(profile, options(CvTemplate.PROFESSIONAL)).photo)
    }
}
