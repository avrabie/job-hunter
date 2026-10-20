package net.s4v3.backend

import net.s4v3.backend.accounts.CurrentUser
import net.s4v3.backend.accounts.NoCurrentUserException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFailsWith

@ApiTest
class CurrentUserTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val currentUser: CurrentUser,
) {

    @Test
    fun `inside a request the current user id is the token subject`() {
        mvc.perform(get("/api/test/me").header("Authorization", "Bearer ${issuer.token("user-42")}"))
            .andExpect(status().isOk)
            .andExpect(content().string("user-42"))
    }

    @Test
    fun `outside a request asking for the current user fails`() {
        assertFailsWith<NoCurrentUserException> { currentUser.id() }
    }

    private val issuer get() = SharedMockIssuer.instance
}
