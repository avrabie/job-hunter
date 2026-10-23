package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.Profile
import net.s4v3.backend.profile.ProfileInvalidException
import net.s4v3.backend.profile.ProfileUpdate
import net.s4v3.backend.profile.Publication
import java.time.LocalDate
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
}
