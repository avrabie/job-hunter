package net.s4v3.backend.cv.publiccv.internal

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

/** Every route of the UI is reserved, so no user can publish a page under an app address (`cv-public` "Handle"). */
internal class ReservedHandlesTests {

    private val app: Path = Path.of("..", "ui", "src", "App.tsx")

    @Test
    fun `every first segment of a UI route is a reserved handle`() {
        assertTrue(Files.isRegularFile(app), "UI routes not found at ${app.toAbsolutePath()}")
        val source = Files.readString(app)
        val routePath = Regex("path=\"/([^\"]*)\"")
        val segments = routePath.findAll(source)
            .map { it.groupValues[1].substringBefore('/') }
            .filter { it.isNotEmpty() }
            .toList()
        assertTrue(segments.isNotEmpty(), "no route paths found in App.tsx")

        val unreserved = segments.filter { it !in HandleRules.RESERVED }

        assertTrue(unreserved.isEmpty(), "UI route segments missing from the reserved handles: $unreserved")
    }
}
