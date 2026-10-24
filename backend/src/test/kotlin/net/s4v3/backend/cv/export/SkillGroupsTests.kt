package net.s4v3.backend.cv.export

import net.s4v3.backend.cv.export.internal.Labels
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillGroupsTests {

    private fun group(name: String) = SkillGroups.group(name)

    @Test
    fun `spec examples land in their groups`() {
        assertEquals(SkillGroup.LANGUAGES, group("Java"))
        assertEquals(SkillGroup.MESSAGING, group("Kafka"))
        assertEquals(SkillGroup.DATABASES, group("PostgreSQL"))
        assertEquals(SkillGroup.OTHER, group("Basket weaving"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(SkillGroup.MESSAGING, group("KAFKA"))
        assertEquals(SkillGroup.DATABASES, group("postgresql"))
        assertEquals(SkillGroup.CLOUD, group("  Docker  "))
    }

    @Test
    fun `keywords match whole tokens only`() {
        assertEquals(SkillGroup.LANGUAGES, group("Javascript"))
        assertEquals(SkillGroup.OTHER, group("Javaz"), "java must not match inside another word")
        assertEquals(SkillGroup.CLOUD, group("Docker"))
        assertEquals(SkillGroup.OTHER, group("Cobol"), "c must not match inside another word")
        assertEquals(SkillGroup.OTHER, group("Golang"))
        assertEquals(SkillGroup.LANGUAGES, group("Go"))
    }

    @Test
    fun `token boundaries are spaces, punctuation and ends`() {
        assertEquals(SkillGroup.LANGUAGES, group("Kotlin, Java"))
        assertEquals(SkillGroup.LANGUAGES, group("Kotlin (JVM)"))
        assertEquals(SkillGroup.MESSAGING, group("Kafka."))
        assertEquals(SkillGroup.TOOLS, group("Git / GitLab"))
    }

    @Test
    fun `symbol names match as whole names`() {
        assertEquals(SkillGroup.LANGUAGES, group("C++"))
        assertEquals(SkillGroup.LANGUAGES, group("C#"))
        assertEquals(SkillGroup.LANGUAGES, group("C"))
        assertEquals(SkillGroup.LANGUAGES, group("PL/SQL"))
        assertEquals(SkillGroup.TOOLS, group("CI/CD"))
    }

    @Test
    fun `a name that matches several groups goes to the first in print order`() {
        assertEquals(SkillGroup.MESSAGING, group("Docker Kafka"), "messaging is printed before cloud")
        assertEquals(SkillGroup.CLOUD, group("Git Docker"), "cloud is printed before tools")
        assertEquals(SkillGroup.LANGUAGES, group("Kafka Java"), "languages come first")
    }

    @Test
    fun `unknown names go to other`() {
        assertEquals(SkillGroup.OTHER, group("Basket weaving"))
        assertEquals(SkillGroup.OTHER, group(""))
        assertEquals(SkillGroup.OTHER, group("   "))
    }

    @Test
    fun `group labels are fixed in English and German`() {
        val en = SkillGroup.entries.map { Labels.skillGroup(it, CvLanguage.EN) }
        assertEquals(
            listOf(
                "Programming languages",
                "Frameworks",
                "Messaging",
                "Security & identity",
                "Cloud & containers",
                "Tests & observability",
                "Databases",
                "Tools",
                "Other",
            ),
            en,
        )
        val de = SkillGroup.entries.map { Labels.skillGroup(it, CvLanguage.DE) }
        assertEquals(
            listOf(
                "Programmiersprachen",
                "Frameworks",
                "Messaging",
                "Sicherheit & Identität",
                "Cloud & Container",
                "Tests & Observability",
                "Datenbanken",
                "Werkzeuge",
                "Weitere",
            ),
            de,
        )
    }
}
