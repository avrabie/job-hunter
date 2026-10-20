package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ApiTest
class HealthEndpointTests(@Autowired private val mvc: MockMvc) {

    @Test
    fun `liveness and readiness are up without a token when the database is reachable`() {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("UP"))
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk).andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `the public health endpoint reveals only the status, not component details`() {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist())
    }

    @Test
    fun `other actuator endpoints are not reachable without a token`() {
        listOf("env", "beans", "metrics", "info", "applicationmodules").forEach { name ->
            mvc.perform(get("/actuator/$name")).andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `other actuator endpoints are not exposed even with a token`() {
        val token = SharedMockIssuer.instance.token("user-health", roles = listOf("USER", "ADMIN"))
        listOf("env", "beans", "metrics", "applicationmodules").forEach { name ->
            mvc.perform(get("/actuator/$name").header("Authorization", "Bearer $token")).andExpect(status().isNotFound)
        }
    }
}
