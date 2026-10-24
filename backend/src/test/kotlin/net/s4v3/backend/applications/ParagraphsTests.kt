package net.s4v3.backend.applications

import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The same table as the UI test: `ui/src/applications/paragraphs.cases.json` is read in place so the two cannot drift. */
class ParagraphsTests {

    private fun casesFile(): Path {
        var dir: Path? = Path.of("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve("ui/src/applications/paragraphs.cases.json")
            if (Files.exists(candidate)) return candidate
            dir = dir.parent
        }
        error("ui/src/applications/paragraphs.cases.json not found")
    }

    @Test
    fun `paragraphs passes every case of the shared table`() {
        val cases = JsonMapper.builder().build().readTree(Files.readString(casesFile()))
        assertTrue(cases.size() >= 10, "the table is suspiciously short")
        for (index in 0 until cases.size()) {
            val case = cases.get(index)
            val expected = mutableListOf<String>()
            for (i in 0 until case.path("paragraphs").size()) expected += case.path("paragraphs").get(i).asString()
            assertEquals(expected, paragraphs(case.path("text").asString()), case.path("name").asString())
        }
    }
}
