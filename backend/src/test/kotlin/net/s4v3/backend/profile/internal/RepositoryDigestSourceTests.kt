package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.DigestResult
import net.s4v3.backend.profile.RepositoryDigest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RepositoryDigestSourceTests {

    private val digest = RepositoryDigest(
        provider = CodeProvider.GITHUB,
        fullName = "octocat/hello-world",
        readme = "# Hello",
        paths = listOf("README.md", "build.gradle.kts"),
        files = mapOf("build.gradle.kts" to "plugins { kotlin(\"jvm\") }"),
        truncated = false,
    )

    /** Records every full name it is asked for and answers with [answer]. */
    private class FakeProvider(
        override val provider: CodeProvider = CodeProvider.GITHUB,
        var answer: DigestResult,
    ) : RepositoryDigestProvider {
        val asked = mutableListOf<String>()

        override fun digest(fullName: String): DigestResult {
            asked += fullName
            return answer
        }
    }

    @Test
    fun `a found repository returns its digest`() {
        val fake = FakeProvider(answer = DigestResult.Found(digest))
        val source = DefaultRepositoryDigestSource(listOf(fake))

        val answer = assertIs<DigestResult.Found>(source.digest(CodeProvider.GITHUB, "octocat/hello-world"))
        assertEquals(digest, answer.digest)
        assertEquals(listOf("octocat/hello-world"), fake.asked)
    }

    @Test
    fun `invalid names never reach the provider`() {
        val fake = FakeProvider(answer = DigestResult.Found(digest))
        val source = DefaultRepositoryDigestSource(listOf(fake))

        listOf(
            "../x",
            "a/b/c",
            "",
            "octocat",
            "octocat/",
            "/hello-world",
            "octocat/.",
            "octocat/..",
            "../..",
            "octo cat/hello",
            "octocat/hello?x=1",
            "octocat/" + "a".repeat(101),
            "a".repeat(101) + "/hello",
        ).forEach { fullName ->
            assertEquals(DigestResult.InvalidName, source.digest(CodeProvider.GITHUB, fullName), fullName)
        }
        assertEquals(emptyList(), fake.asked)
    }

    @Test
    fun `names of one hundred characters and the allowed symbols are valid`() {
        val fake = FakeProvider(answer = DigestResult.NotFound)
        val source = DefaultRepositoryDigestSource(listOf(fake))

        source.digest(CodeProvider.GITHUB, "a".repeat(100) + "/my.repo_name-1")

        assertEquals(listOf("a".repeat(100) + "/my.repo_name-1"), fake.asked)
    }

    @Test
    fun `a second request for the same repository is served from the cache, ignoring case`() {
        val fake = FakeProvider(answer = DigestResult.Found(digest))
        val source = DefaultRepositoryDigestSource(listOf(fake))

        source.digest(CodeProvider.GITHUB, "Octocat/Hello-World")
        val again = source.digest(CodeProvider.GITHUB, "octocat/hello-world")

        assertEquals(digest, assertIs<DigestResult.Found>(again).digest)
        assertEquals(1, fake.asked.size)
    }

    @Test
    fun `failures are not cached`() {
        val fake = FakeProvider(answer = DigestResult.RateLimited)
        val source = DefaultRepositoryDigestSource(listOf(fake))

        assertEquals(DigestResult.RateLimited, source.digest(CodeProvider.GITHUB, "octocat/hello-world"))
        fake.answer = DigestResult.Found(digest)
        assertIs<DigestResult.Found>(source.digest(CodeProvider.GITHUB, "octocat/hello-world"))
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun `not found and unavailable answers are not cached either`() {
        val fake = FakeProvider(answer = DigestResult.NotFound)
        val source = DefaultRepositoryDigestSource(listOf(fake))

        source.digest(CodeProvider.GITHUB, "octocat/ghost")
        fake.answer = DigestResult.Unavailable
        source.digest(CodeProvider.GITHUB, "octocat/ghost")
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun `two providers for the same value are refused at start-up`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultRepositoryDigestSource(
                listOf(
                    FakeProvider(answer = DigestResult.NotFound),
                    FakeProvider(answer = DigestResult.NotFound),
                ),
            )
        }
    }
}
