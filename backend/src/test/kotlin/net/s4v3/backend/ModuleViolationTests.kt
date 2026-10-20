package net.s4v3.backend

import com.tngtech.archunit.core.importer.ImportOption
import net.s4v3.fixture.modules.FixtureApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.core.Violations
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModuleViolationTests {

    @Test
    fun `access to another module's internal type fails and names both modules`() {
        val message = violation.message.orEmpty()

        assertTrue("non-exposed" in message, message)
        assertTrue("alpha" in message && "beta" in message, message)
    }

    @Test
    fun `a dependency cycle fails and names both modules`() {
        val message = violation.message.orEmpty()

        assertTrue("ycle" in message, message)
        assertTrue("alpha" in message && "beta" in message, message)
    }

    private companion object {
        // Modulith caches the verified model, so verify() is evaluated once and both tests inspect the result.
        // The fixture lives in test sources, which Modulith skips unless every location is imported.
        val violation: Violations = assertFailsWith<Violations> {
            ApplicationModules.of(FixtureApplication::class.java, ImportOption { true }).verify()
        }
    }
}
