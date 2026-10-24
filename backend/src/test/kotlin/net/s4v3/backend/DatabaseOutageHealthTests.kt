package net.s4v3.backend

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Stops its database, so it keeps a container of its own (the only one besides SharedPostgres). */
@Tag("integration")
class DatabaseOutageHealthTests {

    @Test
    fun `when the database goes away readiness is 503 and liveness stays 200`() {
        val postgres = SharedPostgres.newContainer().also { it.start(); SharedPostgres.awaitConnection(it) }
        try {
            SpringApplicationBuilder(BackendApplication::class.java)
                .profiles("test")
                .properties(
                    "server.port=0",
                    "spring.datasource.url=${postgres.jdbcUrl}",
                    "spring.datasource.username=${postgres.username}",
                    "spring.datasource.password=${postgres.password}",
                    "spring.datasource.hikari.connection-timeout=1500",
                )
                .run().use { context ->
                    val port = context.environment.getProperty("local.server.port")
                    val client = RestClient.create("http://localhost:$port")
                    fun status(path: String) = client.get().uri(path).retrieve().toBodilessEntity().statusCode.value()

                    assertEquals(200, status("/actuator/health/readiness"))

                    postgres.stop()

                    assertFailsWith<HttpServerErrorException.ServiceUnavailable> { status("/actuator/health/readiness") }
                    assertEquals(200, status("/actuator/health/liveness"))
                }
        } finally {
            postgres.stop()
        }
    }
}
