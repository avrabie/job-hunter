package net.s4v3.backend

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@ApiTest
class PerUserIsolationTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private val issuer get() = SharedMockIssuer.instance

    private val userA = "user-a-" + UUID.randomUUID()
    private val userB = "user-b-" + UUID.randomUUID()

    @BeforeEach
    fun table() {
        jdbc.execute("create table if not exists test_note (id bigserial primary key, owner_sub text not null, text text not null)")
    }

    private fun bearer(sub: String) = "Bearer ${issuer.token(sub)}"

    private fun createNote(sub: String, text: String): Long {
        val body = mvc.perform(post("/api/test/notes").header("Authorization", bearer(sub)).contentType(MediaType.TEXT_PLAIN).content(text))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()
    }

    @Test
    fun `user A gets 404 for user B's record, the same as for a record that does not exist`() {
        val bNote = createNote(userB, "B's secret")

        mvc.perform(get("/api/test/notes/$bNote").header("Authorization", bearer(userA))).andExpect(status().isNotFound)
        mvc.perform(get("/api/test/notes/999999999").header("Authorization", bearer(userA))).andExpect(status().isNotFound)
        mvc.perform(get("/api/test/notes/$bNote").header("Authorization", bearer(userB))).andExpect(status().isOk)
    }

    @Test
    fun `a listing contains only the caller's records`() {
        createNote(userA, "A1")
        createNote(userA, "A2")
        createNote(userB, "B1")

        mvc.perform(get("/api/test/notes").header("Authorization", bearer(userA)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.text == 'B1')]").isEmpty)
    }
}
