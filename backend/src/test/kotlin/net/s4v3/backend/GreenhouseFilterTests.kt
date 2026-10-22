package net.s4v3.backend

import net.s4v3.backend.sources.internal.SourcesSettings
import net.s4v3.backend.sources.internal.greenhouse.RoleFilter
import net.s4v3.backend.sources.internal.greenhouse.SwissLocation
import net.s4v3.backend.sources.internal.greenhouse.SwissPlace
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GreenhouseFilterTests {

    private val defaults = SourcesSettings.Greenhouse()
    private val roles = RoleFilter(defaults.itKeywords, defaults.excludedKeywords)

    @Test
    fun `a city alone is Swiss`() {
        assertEquals(SwissPlace("Zurich", "ZH"), SwissLocation.find("Zurich; "))
    }

    @Test
    fun `any Swiss place of several counts`() {
        assertEquals(SwissPlace("Geneva", "GE"), SwissLocation.find("Geneva; Taipei; Skopje"))
        assertEquals(SwissPlace("Geneva", "GE"), SwissLocation.find("Paris; Geneva; Barcelona"))
    }

    @Test
    fun `umlauts and country names work`() {
        assertEquals(SwissPlace("Zurich", "ZH"), SwissLocation.find("Zürich, Switzerland"))
        assertEquals(SwissPlace(null, null), SwissLocation.find("Remote - Switzerland"))
        assertEquals(SwissPlace(null, null), SwissLocation.find("Switzerland"))
    }

    @Test
    fun `other places, look-alikes and empty text are not Swiss`() {
        assertNull(SwissLocation.find("Warsaw"))
        assertNull(SwissLocation.find("Berne, Indiana"))
        assertNull(SwissLocation.find("Bernardo"))
        assertNull(SwissLocation.find(""))
        assertNull(SwissLocation.find(null))
    }

    @Test
    fun `engineering titles are kept`() {
        assertTrue(roles.isIt("Senior Site Reliability Engineer, Infrastructure"))
        assertTrue(roles.isIt("Working Student, Security Engineer"))
        assertTrue(roles.isIt("Engineering Manager, Partner Tech"))
    }

    @Test
    fun `business titles are dropped`() {
        assertFalse(roles.isIt("Head of Growth"))
        assertFalse(roles.isIt("Group Product Manager, B2C"))
    }

    @Test
    fun `an excluded keyword wins`() {
        assertFalse(roles.isIt("Sales Engineer"))
    }

    @Test
    fun `keywords are configurable`() {
        val analysts = RoleFilter(listOf("analyst"), emptyList())

        assertTrue(analysts.isIt("Data Analyst"))
        assertFalse(analysts.isIt("Software Engineer"))
    }

    @Test
    fun `keywords match whole words only`() {
        assertFalse(roles.isIt("Sresident Gardener"))
    }
}
