package net.s4v3.backend.applications

import net.s4v3.backend.applications.internal.letterFileName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LetterFileNameTests {

    @Test
    fun `names are plain ASCII with accents stripped`() {
        assertEquals("motivation-employer1-ag-backend-engineer-m-w-d.md", letterFileName("Employer1 AG", "Backend Engineer (m/w/d)"))
        assertEquals("motivation-muller-strasse-gmbh-entwickler-in.md", letterFileName("Müller Straße GmbH", "Entwickler/in"))
    }

    @Test
    fun `empty parts fall back and long parts are cut`() {
        assertEquals("motivation-company-job.md", letterFileName("???", ""))
        val name = letterFileName("c".repeat(200), "t\"; x=\r\n".repeat(50))
        assertTrue(name.length <= 95 && Regex("[a-z0-9.-]+").matches(name), name)
    }
}
