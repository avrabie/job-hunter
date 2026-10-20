package net.s4v3.backend

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class KoogBoundaryTests {

    private val koogOnlyInLlm: ArchRule =
        noClasses()
            .that().resideOutsideOfPackage("net.s4v3.backend.llm..")
            .should().dependOnClassesThat().resideInAPackage("ai.koog..")
            .because("only the llm module may use Koog; other modules call llm's public API")

    @Test
    fun `no module outside llm depends on Koog`() {
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("net.s4v3.backend")

        koogOnlyInLlm.check(production)
    }

    @Test
    fun `the rule fails for a class outside llm that uses Koog`() {
        val fixture = ClassFileImporter().importPackages("net.s4v3.fixture.koog", "ai.koog.fake")

        assertFailsWith<AssertionError> { koogOnlyInLlm.check(fixture) }
    }
}
