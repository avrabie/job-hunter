package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileUpdate
import net.s4v3.backend.profile.Project
import net.s4v3.backend.profile.ProjectLink
import net.s4v3.backend.profile.ProjectType
import net.s4v3.backend.profile.Publication
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProfileValidatorTests {

    private val thisYear = LocalDate.now().year

    private fun validate(vararg publications: Publication) =
        ProfileValidator.validate(ProfileUpdate(0, Profile(publications = publications.toList())))

    private fun fieldsOf(vararg publications: Publication): Map<String, String> =
        assertFailsWith<ProfileInvalidException> { validate(*publications) }.fields

    @Test
    fun `a valid publication with every field passes`() {
        validate(
            Publication(
                title = "Fast Joins on Skewed Data",
                venue = "VLDB",
                year = 2021,
                authors = "J. Doe, A. Smith",
                url = "https://example.org/papers/fast-joins",
            ),
        )
    }

    @Test
    fun `a publication with only a title passes`() {
        validate(Publication(title = "Notes on Indexing"))
    }

    @Test
    fun `a blank title is reported by path`() {
        assertEquals("must not be blank", fieldsOf(Publication(title = "  ")).getValue("publications[0].title"))
    }

    @Test
    fun `a year of 1850 is out of range`() {
        assertTrue("publications[0].year" in fieldsOf(Publication(title = "Old", year = 1850)))
    }

    @Test
    fun `a year two above the current year is out of range`() {
        assertTrue("publications[0].year" in fieldsOf(Publication(title = "Far", year = thisYear + 2)))
    }

    @Test
    fun `a year of next year is accepted`() {
        validate(Publication(title = "Forthcoming", year = thisYear + 1))
    }

    @Test
    fun `a javascript link is rejected`() {
        assertTrue("publications[0].url" in fieldsOf(Publication(title = "X", url = "javascript:alert(1)")))
    }

    @Test
    fun `a relative link is rejected`() {
        assertTrue("publications[0].url" in fieldsOf(Publication(title = "X", url = "/papers/1")))
    }

    @Test
    fun `a link over 500 characters is rejected`() {
        val url = "https://example.org/" + "a".repeat(500)
        assertTrue("publications[0].url" in fieldsOf(Publication(title = "X", url = url)))
    }

    @Test
    fun `a failing field never echoes the submitted value`() {
        val marker = "SECRET-MARKER"
        val fields = fieldsOf(Publication(title = " ", venue = marker.repeat(50), year = 1, authors = "a".repeat(501), url = "javascript:$marker"))

        assertEquals(5, fields.size)
        assertTrue(fields.values.none { marker in it })
    }

    @Test
    fun `101 publications are rejected`() {
        val entry = Publication(title = "T")

        assertTrue("publications" in fieldsOf(*Array(101) { entry }))
    }

    @Test
    fun `100 publications are accepted`() {
        val entry = Publication(title = "T")

        validate(*Array(100) { entry })
    }

    private fun validateProfile(profile: Profile) = ProfileValidator.validate(ProfileUpdate(0, profile))

    private fun profileFields(profile: Profile): Map<String, String> =
        assertFailsWith<ProfileInvalidException> { validateProfile(profile) }.fields

    private fun experienceWith(vararg technologies: String) =
        Profile(experience = listOf(Experience("Example AG", "Engineer", YearMonth.of(2020, 1), technologies = technologies.toList())))

    private fun projectWith(vararg links: ProjectLink) =
        Profile(projects = listOf(Project("Store", ProjectType.ASSIGNMENT, links = links.toList())))

    @Test
    fun `technologies are kept as sent, any casing`() {
        validateProfile(experienceWith("Kotlin", "Spring Boot", "kotlin"))
    }

    @Test
    fun `a blank technology is reported by path`() {
        assertEquals("must not be blank", profileFields(experienceWith("Kotlin", " ")).getValue("experience[0].technologies[1]"))
    }

    @Test
    fun `a technology of 61 characters is rejected and 60 is accepted`() {
        validateProfile(experienceWith("k".repeat(60)))
        assertTrue("experience[0].technologies[0]" in profileFields(experienceWith("k".repeat(61))))
    }

    @Test
    fun `31 technologies are rejected by list`() {
        assertTrue("experience[0].technologies" in profileFields(experienceWith(*Array(31) { "T$it" })))
    }

    @Test
    fun `a valid project with every field passes`() {
        validateProfile(
            Profile(
                projects = listOf(
                    Project(
                        name = "Distributed key-value store",
                        type = ProjectType.ASSIGNMENT,
                        context = "ETH Zurich, Distributed Systems",
                        start = YearMonth.of(2023, 2),
                        end = YearMonth.of(2023, 6),
                        description = "Raft-based store with snapshots.",
                        technologies = listOf("Kotlin", "gRPC"),
                        results = listOf("Grade 6.0", "Handled 20k writes/s in the benchmark"),
                        links = listOf(ProjectLink("Repository", "https://github.com/example/kv-store")),
                        highlight = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a project with only a name and a type passes`() {
        validateProfile(Profile(projects = listOf(Project("Notes app", ProjectType.PERSONAL))))
    }

    @Test
    fun `a blank project name is reported by path`() {
        assertEquals("must not be blank", profileFields(Profile(projects = listOf(Project(" ", ProjectType.WORK)))).getValue("projects[0].name"))
    }

    @Test
    fun `a project that starts after it ends is reported at its end`() {
        val project = Project("Late", ProjectType.OPEN_SOURCE, start = YearMonth.of(2024, 5), end = YearMonth.of(2024, 1))

        assertTrue("projects[0].end" in profileFields(Profile(projects = listOf(project))))
    }

    @Test
    fun `a javascript project link is rejected by path`() {
        assertTrue("projects[0].links[0].url" in profileFields(projectWith(ProjectLink("Demo", "javascript:alert(1)"))))
    }

    @Test
    fun `a project link without a label is rejected`() {
        assertTrue("projects[0].links[0].label" in profileFields(projectWith(ProjectLink(" ", "https://example.org"))))
    }

    @Test
    fun `51 projects are rejected and 50 are accepted`() {
        val project = Project("P", ProjectType.WORK)

        validateProfile(Profile(projects = List(50) { project }))
        assertTrue("projects" in profileFields(Profile(projects = List(51) { project })))
    }

    @Test
    fun `6 links on a project are rejected and 5 are accepted`() {
        val link = ProjectLink("Repository", "https://example.org/x")

        validateProfile(projectWith(*Array(5) { link }))
        assertTrue("projects[0].links" in profileFields(projectWith(*Array(6) { link })))
    }

    @Test
    fun `a failing project field never echoes the submitted value`() {
        val marker = "SECRET-MARKER"
        val fields = profileFields(
            Profile(projects = listOf(Project(" ", ProjectType.WORK, context = marker.repeat(50), links = listOf(ProjectLink("L", "javascript:$marker"))))),
        )

        assertTrue(fields.isNotEmpty())
        assertTrue(fields.values.none { marker in it })
    }
}
