package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FailingMigrationTests {

    @Test
    fun `a failing changeset stops startup and the error names it`() {
        val failure = assertFailsWith<Exception> {
            SpringApplicationBuilder(BackendApplication::class.java, TestcontainersConfiguration::class.java)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties("spring.liquibase.change-log=classpath:/db/broken/db.changelog-master.yaml")
                .run()
                .close()
        }

        val messages = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { "999-deliberately-broken" in it }, messages.toString())
    }
}
