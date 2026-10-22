package net.s4v3.backend

import net.s4v3.backend.matching.internal.MatchResult
import net.s4v3.backend.matching.internal.MatchStore
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.profile.ProfileService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ApiTest
internal class MatchesApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val profiles: ProfileService,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val store: MatchStore,
) {

    private val support = MatchingTestSupport(jdbc, profiles, ingest)
    private val issuer get() = SharedMockIssuer.instance
    private fun bearer(sub: String) = "Bearer " + issuer.token(sub)

    private fun call(sub: String, query: String = "") = mvc.perform(get("/api/matches$query").header("Authorization", bearer(sub)))

    private fun add(sub: String, posting: java.util.UUID, passed: Boolean, score: Int) =
        store.upsert(sub, posting, MatchResult(passed, score, listOf("ROLE_MATCH", "SKILL_MATCH_2")), 1)

    @Test
    fun `no token gets 401`() {
        mvc.perform(get("/api/matches")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `each user receives exactly their own passing matches`() {
        val a = support.newUser()
        val b = support.newUser()
        val p1 = support.newPosting(title = "A one")
        val p2 = support.newPosting(title = "A two")
        val p3 = support.newPosting(title = "B one")
        add(a, p1, true, 80); add(a, p2, true, 60); add(a, p3, false, 99)
        add(b, p3, true, 50)

        call(a).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].postingId").value(p1.toString()))
            .andExpect(jsonPath("$[0].title").value("A one"))
            .andExpect(jsonPath("$[0].score").value(80))
            .andExpect(jsonPath("$[0].reasons[0]").value("ROLE_MATCH"))
            .andExpect(jsonPath("$[0].url").exists())
            .andExpect(jsonPath("$[1].postingId").value(p2.toString()))
        call(b).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].postingId").value(p3.toString()))
    }

    @Test
    fun `paging and the size cap`() {
        val a = support.newUser()
        val postings = (1..3).map { support.newPosting(title = "Posting $it") }
        postings.forEachIndexed { i, p -> add(a, p, true, 90 - i) }

        call(a, "?size=1").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].postingId").value(postings[0].toString()))
        call(a, "?size=1&page=1").andExpect(jsonPath("$[0].postingId").value(postings[1].toString()))
        call(a, "?size=1000").andExpect(status().isOk).andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `a bad page is a 400 and a user without matches gets an empty list`() {
        val a = support.newUser()

        call(a, "?page=-1").andExpect(status().isBadRequest)
        call(a, "?page=abc").andExpect(status().isBadRequest)
        call(a).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `the response holds no profile data`() {
        val a = support.newUser()
        support.saveProfile(a, skills = listOf("Secretskill"))
        val p = support.newPosting()
        add(a, p, true, 70)

        val body = call(a).andReturn().response.contentAsString

        kotlin.test.assertFalse("Secretskill" in body)
        kotlin.test.assertFalse(a in body)
    }
}
