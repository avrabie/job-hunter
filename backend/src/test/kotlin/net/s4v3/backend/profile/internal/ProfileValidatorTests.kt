package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeAccount
import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.Experience
import net.s4v3.backend.profile.OpenSourceProject
import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileUpdate
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

    private fun accounts(vararg accounts: CodeAccount) = Profile(codeAccounts = accounts.toList())

    private fun repository(
        fullName: String = "octocat/hello-world",
        url: String = "https://github.com/octocat/hello-world",
        provider: CodeProvider = CodeProvider.GITHUB,
    ) = OpenSourceProject(provider = provider, fullName = fullName, name = fullName.substringAfter('/'), url = url)

    private fun repositories(vararg projects: OpenSourceProject) = Profile(openSourceProjects = projects.toList())

    @Test
    fun `a valid GitHub account passes`() {
        validateProfile(accounts(CodeAccount(CodeProvider.GITHUB, "octocat")))
    }

    @Test
    fun `usernames at the edges of the GitHub rule pass`() {
        validateProfile(accounts(CodeAccount(CodeProvider.GITHUB, "a".repeat(39))))
        validateProfile(accounts(CodeAccount(CodeProvider.GITHUB, "a-b-C9")))
    }

    @Test
    fun `bad GitHub usernames are rejected by path`() {
        listOf("-bad-", "a--b", "", "a".repeat(40), "under_score", "dot.name", "sp ace").forEach { username ->
            assertTrue(
                "codeAccounts[0].username" in profileFields(accounts(CodeAccount(CodeProvider.GITHUB, username))),
                "username of length ${username.length} should be rejected",
            )
        }
    }

    @Test
    fun `a rejected username never echoes the submitted value`() {
        val marker = "SECRET-MARKER"
        val fields = profileFields(accounts(CodeAccount(CodeProvider.GITHUB, "$marker-$marker-")))

        assertTrue(fields.values.none { marker in it })
    }

    @Test
    fun `two accounts for one provider are rejected at the second provider`() {
        val fields = profileFields(accounts(CodeAccount(CodeProvider.GITHUB, "octocat"), CodeAccount(CodeProvider.GITHUB, "hubot")))

        assertTrue("codeAccounts[1].provider" in fields)
    }

    @Test
    fun `a valid project with every field passes`() {
        validateProfile(
            repositories(
                OpenSourceProject(
                    provider = CodeProvider.GITHUB,
                    fullName = "octocat/hello-world",
                    name = "hello-world",
                    url = "https://github.com/octocat/hello-world",
                    description = "My first repository",
                    language = "Kotlin",
                    topics = listOf("demo", "kotlin"),
                    stars = 42,
                    note = "Maintainer since 2022",
                ),
            ),
        )
    }

    @Test
    fun `a project with only provider, full name, name and url passes`() {
        validateProfile(repositories(repository()))
    }

    @Test
    fun `javascript and plain http project addresses are rejected by path`() {
        assertTrue("openSourceProjects[0].url" in profileFields(repositories(repository(url = "javascript:alert(1)"))))
        assertTrue("openSourceProjects[0].url" in profileFields(repositories(repository(url = "http://github.com/x/y"))))
    }

    @Test
    fun `a duplicate repository is rejected at its full name`() {
        val fields = profileFields(repositories(repository(), repository()))

        assertTrue("openSourceProjects[1].fullName" in fields)
    }

    @Test
    fun `30 open source projects pass and 31 are rejected`() {
        val entry = repository()
        validateProfile(Profile(openSourceProjects = List(30) { repository("octocat/r$it") }))
        assertTrue("openSourceProjects" in profileFields(Profile(openSourceProjects = List(31) { entry })))
    }

    @Test
    fun `description of 500 characters passes and 501 is rejected`() {
        validateProfile(repositories(repository().copy(description = "d".repeat(500))))
        assertTrue("openSourceProjects[0].description" in profileFields(repositories(repository().copy(description = "d".repeat(501)))))
    }

    @Test
    fun `language of 61 characters is rejected`() {
        assertTrue("openSourceProjects[0].language" in profileFields(repositories(repository().copy(language = "l".repeat(61)))))
    }

    @Test
    fun `topics are limited to 20 and each to 60 characters`() {
        validateProfile(repositories(repository().copy(topics = List(20) { "t$it" })))
        assertTrue("openSourceProjects[0].topics" in profileFields(repositories(repository().copy(topics = List(21) { "t$it" }))))
        assertTrue("openSourceProjects[0].topics[0]" in profileFields(repositories(repository().copy(topics = listOf("t".repeat(61))))))
    }

    @Test
    fun `negative stars are rejected and zero passes`() {
        validateProfile(repositories(repository().copy(stars = 0)))
        assertTrue("openSourceProjects[0].stars" in profileFields(repositories(repository().copy(stars = -1))))
    }

    @Test
    fun `a note of 2000 characters passes and 2001 is rejected`() {
        validateProfile(repositories(repository().copy(note = "n".repeat(2000))))
        assertTrue("openSourceProjects[0].note" in profileFields(repositories(repository().copy(note = "n".repeat(2001)))))
    }

    @Test
    fun `a failing repository field never echoes the submitted value`() {
        val marker = "SECRET-MARKER"
        val fields = profileFields(
            repositories(repository(url = "javascript:$marker").copy(description = marker.repeat(200), stars = -1, note = " ")),
        )

        assertTrue(fields.isNotEmpty())
        assertTrue(fields.values.none { marker in it })
    }
}
