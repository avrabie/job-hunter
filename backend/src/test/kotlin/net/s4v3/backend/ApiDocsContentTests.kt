package net.s4v3.backend

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ApiTest
class ApiDocsContentTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val context: org.springframework.context.ApplicationContext,
) {

    private val json = JsonMapper.builder().build()
    private fun rawDocument() = mvc.perform(get("/api/openapi.json")).andReturn().response.contentAsString
    private fun document(): JsonNode = json.readTree(rawDocument())
    private fun operation(doc: JsonNode, method: String, path: String): JsonNode = doc.path("paths").path(path).path(method)

    @AfterEach
    fun clean() {
        jdbc.update("delete from crawl_schedule")
    }

    @Test
    fun `the profile endpoints are described with their error statuses`() {
        val doc = document()

        val getOp = operation(doc, "get", "/api/profile")
        val putOp = operation(doc, "put", "/api/profile")
        assertEquals(false, getOp.isMissingNode)
        assertEquals(false, putOp.isMissingNode)
        assertEquals(listOf("200", "400", "401", "409", "413"), putOp.path("responses").propertyNames().toList().sorted())
        assertEquals(false, putOp.path("requestBody").isMissingNode)
        assertEquals(false, doc.path("components").path("schemas").path("StoredProfile").isMissingNode)
    }

    @Test
    fun `the admin endpoints are described with the ADMIN requirement`() {
        val doc = document()

        for (method in listOf("get", "post")) {
            val op = operation(doc, method, "/api/admin/crawl-schedule")
            assertEquals(false, op.isMissingNode, method)
            assertTrue("ADMIN" in op.path("description").asString(), method)
            val statuses = op.path("responses").propertyNames().toList()
            assertTrue("401" in statuses && "403" in statuses, "$method: $statuses")
        }
        assertTrue("400" in operation(doc, "post", "/api/admin/crawl-schedule").path("responses").propertyNames().toList())
    }

    @Test
    fun `a bearer scheme is declared and operations use it`() {
        val doc = document()

        assertEquals("bearer", doc.path("components").path("securitySchemes").path("bearer").path("scheme").asString())
        assertEquals(false, doc.path("security").isMissingNode)
        assertTrue(rawDocument().contains("\"bearer\""))
    }

    @Test
    fun `no stored data appears in the document`() {
        val sub = "docs-user-" + UUID.randomUUID()
        val token = "Bearer " + SharedMockIssuer.instance.token(sub, roles = listOf("USER", "ADMIN"))
        val profile = javaClass.getResourceAsStream("/profile/full-profile.json")!!.readBytes().decodeToString()
        mvc.perform(put("/api/profile").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("""{"version":0,"profile":$profile}"""))
            .andExpect(status().isOk)
        mvc.perform(post("/api/admin/crawl-schedule").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
            .content("""{"enabled":false,"cron":"0 0 3 1 1 *","zone":"UTC"}""")).andExpect(status().isOk)

        val doc = rawDocument()

        assertFalse("Example AG" in doc, "profile content appears in the document")
        assertFalse(sub in doc, "a user id appears in the document")
        assertFalse("0 0 3 1 1 *" in doc, "the stored schedule appears in the document")
    }

    @Test
    fun `every endpoint under api appears in the document with a summary`() {
        val handlerMappings = context.getBeansOfType(RequestMappingHandlerMapping::class.java).values
        assertTrue(handlerMappings.isNotEmpty(), "no request mappings found, so the guard would prove nothing")
        val endpoints = handlerMappings.flatMap { it.handlerMethods.keys }.flatMap { info ->
            val paths = info.pathPatternsCondition?.patterns?.map { it.patternString }.orEmpty()
            paths.filter { it.startsWith("/api/") && !isExcluded(it) }
                .flatMap { path -> info.methodsCondition.methods.map { it.name to path } }
        }
        assertTrue(endpoints.size >= 4, "expected the profile and schedule endpoints, found $endpoints")

        val missing = undocumented(document(), endpoints)

        assertTrue(missing.isEmpty(), "endpoints without a summary in the API description: $missing")
    }

    @Test
    fun `the guard names an endpoint that is missing or has no summary`() {
        val doc = json.readTree(
            """{"paths":{"/api/a":{"get":{"summary":"ok"},"post":{"summary":" "}}}}""",
        )

        val missing = undocumented(doc, listOf("GET" to "/api/a", "POST" to "/api/a", "GET" to "/api/new/thing"))

        assertEquals(listOf("POST /api/a", "GET /api/new/thing"), missing)
    }

    private fun undocumented(doc: JsonNode, endpoints: List<Pair<String, String>>): List<String> =
        endpoints.filter { (method, path) -> operation(doc, method.lowercase(), path).path("summary").asString("").isBlank() }
            .map { (method, path) -> "$method $path" }

    private fun isExcluded(path: String) =
        path.startsWith("/api/test/") || path == "/api/admin/test/unlisted" || path.startsWith("/api/docs") ||
            path.startsWith("/api/swagger-ui") || path.startsWith("/api/openapi")
}
