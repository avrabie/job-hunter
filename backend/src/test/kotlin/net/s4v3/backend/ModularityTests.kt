package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import kotlin.test.assertEquals

class ModularityTests {

    private val modules = ApplicationModules.of(BackendApplication::class.java)

    @Test
    fun `module structure is valid`() {
        modules.verify()
    }

    @Test
    fun `module set is exactly the nine planned modules`() {
        val names = modules.map { it.identifier.toString() }.toSet()

        assertEquals(
            setOf(
                "sources", "postings", "accounts", "profile", "matching",
                "applications", "llm", "web", "notifications",
            ),
            names,
        )
    }
}
