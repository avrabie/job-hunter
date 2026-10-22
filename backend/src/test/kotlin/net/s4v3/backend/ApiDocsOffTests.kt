package net.s4v3.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ApiTest
@TestPropertySource(properties = ["jobhunter.api-docs.enabled=false"])
class ApiDocsOffTests(@Autowired private val mvc: MockMvc) {

    private fun auth() = "Bearer " + SharedMockIssuer.instance.token("docs-off-user")

    @Test
    fun `switched off, the paths do not exist`() {
        mvc.perform(get("/api/openapi.json").header("Authorization", auth())).andExpect(status().isNotFound)
        mvc.perform(get("/api/docs").header("Authorization", auth())).andExpect(status().isNotFound)
    }

    @Test
    fun `switched off, anonymous requests get 401 like any protected path and the API is unchanged`() {
        mvc.perform(get("/api/openapi.json")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/docs")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized)
    }
}
