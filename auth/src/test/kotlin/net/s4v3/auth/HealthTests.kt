package net.s4v3.auth

import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@WebIntegrationTest
class HealthTests(@LocalServerPort port: Int) {
    private val flow = AuthFlow(port)

    @Test
    fun `liveness and readiness are 200 without a token when the database is reachable`() {
        for (probe in listOf("liveness", "readiness")) {
            val response = flow.get("${flow.base}/actuator/health/$probe")

            assertEquals(200, response.status, probe)
            assertEquals("UP", response.json()["status"].asString())
        }
    }

    @Test
    fun `no other actuator endpoint is reachable without authentication`() {
        for (path in listOf("env", "info", "beans", "metrics", "mappings", "configprops", "health")) {
            val response = flow.get("${flow.base}/actuator/$path", "Accept" to "application/json")

            assertTrue(response.status in listOf(401, 404), "$path: ${response.status}")
            assertTrue("password" !in response.body.lowercase())
        }
    }
}

class DatabaseOutageHealthTests {

    @Test
    fun `when the database goes away readiness is 503 and liveness stays 200`() {
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).also { it.start() }
        try {
            SpringApplicationBuilder(AuthApplication::class.java)
                .profiles("test")
                .properties(
                    "spring.datasource.url=${postgres.jdbcUrl}",
                    "spring.datasource.username=${postgres.username}",
                    "spring.datasource.password=${postgres.password}",
                    "spring.datasource.hikari.connection-timeout=1500",
                )
                .run("--server.port=0")
                .use { context ->
                    val port = context.environment.getProperty("local.server.port")
                    val client = RestClient.create("http://localhost:$port/auth")
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
