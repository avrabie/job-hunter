package net.s4v3.backend

import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.assertFalse

@ApiTest
class PostingsApiTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val ingest: PostingIngest,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun token() = "Bearer " + SharedMockIssuer.instance.token("postings-user-" + UUID.randomUUID())
    private fun unique() = "uq" + UUID.randomUUID().toString().replace("-", "").take(10)

    private fun add(title: String, canton: String? = "ZH", raw: String = "{}", source: String = "api-src-" + unique()): UUID =
        ingest.ingest(
            PostingCandidate(
                sourceId = source, sourcePostingId = unique(), url = "https://example.org/$source/" + unique(), title = title,
                company = "Apico " + unique(), rawPayload = raw, city = "Zurich", canton = canton,
                description = "We are looking for an experienced engineer to build and run reliable services with Kotlin. " +
                    "You will work in a small team, review code, and improve our delivery pipeline every week.",
            ),
        ).postingId

    @Test
    fun `no token gets 401 on both endpoints`() {
        mvc.perform(get("/api/postings")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/postings/" + UUID.randomUUID())).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a signed-in user lists postings with dates and links, newest first`() {
        val tag = unique()
        val older = add("Dev $tag one").also { Thread.sleep(20) }
        val newer = add("Dev $tag two")

        mvc.perform(get("/api/postings?q=$tag").header("Authorization", token()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(newer.toString()))
            .andExpect(jsonPath("$[1].id").value(older.toString()))
            .andExpect(jsonPath("$[0].firstSeen").exists())
            .andExpect(jsonPath("$[0].links[0].url").exists())
            .andExpect(jsonPath("$[0].links[0].primary").value(true))
            .andExpect(jsonPath("$[0].description").doesNotExist())
            .andExpect(jsonPath("$[0].userAdded").value(false))
    }

    @Test
    fun `a posting imported by a user is marked userAdded in list and detail`() {
        val id = add("Imported ${unique()}", source = "user-import")

        mvc.perform(get("/api/postings/$id").header("Authorization", token()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userAdded").value(true))
    }

    @Test
    fun `the detail holds the description and links but never the raw payload`() {
        val id = add("Detail ${unique()}", raw = """{"secret":"RAWMARKER9921"}""")

        val body = mvc.perform(get("/api/postings/$id").header("Authorization", token()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").exists())
            .andExpect(jsonPath("$.links.length()").value(1))
            .andReturn().response.contentAsString
        val list = mvc.perform(get("/api/postings?q=Detail").header("Authorization", token())).andReturn().response.contentAsString

        assertFalse("RAWMARKER9921" in body)
        assertFalse("RAWMARKER9921" in list)
    }

    @Test
    fun `an unknown or malformed id is 404`() {
        mvc.perform(get("/api/postings/" + UUID.randomUUID()).header("Authorization", token())).andExpect(status().isNotFound)
        mvc.perform(get("/api/postings/not-a-uuid").header("Authorization", token())).andExpect(status().isNotFound)
    }

    @Test
    fun `bad parameters are 400 and the size is capped`() {
        mvc.perform(get("/api/postings?page=-1").header("Authorization", token())).andExpect(status().isBadRequest)
        mvc.perform(get("/api/postings?canton=ZUR").header("Authorization", token())).andExpect(status().isBadRequest)
        mvc.perform(get("/api/postings?source=a%20b").header("Authorization", token())).andExpect(status().isBadRequest)
        val body = mvc.perform(get("/api/postings?size=1000").header("Authorization", token())).andExpect(status().isOk)
            .andReturn().response.contentAsString
        kotlin.test.assertTrue(body.split("\"title\"").size - 1 <= 100)
    }

    @Test
    fun `filters work through http`() {
        val tag = unique()
        val source = "api-src-" + unique()
        val ge = add("Dev $tag", canton = "GE", source = source)
        add("Dev $tag", canton = "ZH")

        mvc.perform(get("/api/postings?q=$tag&canton=ge&source=$source").header("Authorization", token()))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(ge.toString()))
    }
}
