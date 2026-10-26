package net.s4v3.backend

import net.s4v3.backend.sources.internal.SourcesSettings
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwissDevJobsSettingsTests {

    private fun bind(vararg props: Pair<String, String>): SourcesSettings =
        Binder(MapConfigurationPropertySource(props.toMap())).bind("jobhunter.sources", SourcesSettings::class.java)
            .orElseGet { SourcesSettings() }

    @Test
    fun `the adapter is on by default and points at the board`() {
        val s = bind().swissdevjobs

        assertTrue(s.enabled)
        assertEquals("https://swissdevjobs.ch", s.baseUrl)
    }

    @Test
    fun `both settings can be overridden`() {
        val s = bind(
            "jobhunter.sources.swissdevjobs.enabled" to "false",
            "jobhunter.sources.swissdevjobs.base-url" to "http://localhost:1",
        ).swissdevjobs

        assertEquals(false, s.enabled)
        assertEquals("http://localhost:1", s.baseUrl)
    }
}
