package net.s4v3.backend

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class TokenAccessBoundaryTests {

    private val onlyAccountsReadsTheToken: ArchRule =
        noClasses()
            .that().resideOutsideOfPackage("net.s4v3.backend.accounts..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.security.oauth2.jwt.Jwt")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
            .because("other modules must ask CurrentUser who the caller is")

    @Test
    fun `only accounts reads the token or the security context`() {
        val production = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("net.s4v3.backend")

        onlyAccountsReadsTheToken.check(production)
    }

    @Test
    fun `the rule fails for a class outside accounts that reads the security context`() {
        val fixture = ClassFileImporter().importPackages("net.s4v3.fixture.token")

        assertFailsWith<AssertionError> { onlyAccountsReadsTheToken.check(fixture) }
    }
}
