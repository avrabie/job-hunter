package net.s4v3.backend.profile.internal

import net.s4v3.backend.profile.CodeProvider
import net.s4v3.backend.profile.Repository
import net.s4v3.backend.profile.RepositoryListing
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RepositoryCatalogTests {

    private val hello = Repository(
        provider = CodeProvider.GITHUB,
        fullName = "octocat/hello-world",
        name = "hello-world",
        url = "https://github.com/octocat/hello-world",
        stars = 42,
    )

    /** Records every username it is asked for and answers with [answer]. */
    private class FakeProvider(
        override val provider: CodeProvider = CodeProvider.GITHUB,
        var answer: RepositoryListing,
    ) : RepositoryProvider {
        val asked = mutableListOf<String>()

        override fun list(username: String): RepositoryListing {
            asked += username
            return answer
        }
    }

    @Test
    fun `a found account returns its repositories`() {
        val fake = FakeProvider(answer = RepositoryListing.Found(listOf(hello)))
        val catalog = DefaultRepositoryCatalog(listOf(fake))

        val answer = assertIs<RepositoryListing.Found>(catalog.list(CodeProvider.GITHUB, "octocat"))
        assertEquals(listOf(hello), answer.repositories)
        assertEquals(listOf("octocat"), fake.asked)
    }

    @Test
    fun `an invalid username never reaches the provider`() {
        val fake = FakeProvider(answer = RepositoryListing.Found(listOf(hello)))
        val catalog = DefaultRepositoryCatalog(listOf(fake))

        listOf("../users", "-bad-", "a--b", "", "a".repeat(40), "octo cat").forEach { username ->
            assertEquals(RepositoryListing.InvalidUsername, catalog.list(CodeProvider.GITHUB, username))
        }
        assertEquals(emptyList(), fake.asked)
    }

    @Test
    fun `a second request for the same account is served from the cache, ignoring case`() {
        val fake = FakeProvider(answer = RepositoryListing.Found(listOf(hello)))
        val catalog = DefaultRepositoryCatalog(listOf(fake))

        catalog.list(CodeProvider.GITHUB, "Octocat")
        val again = catalog.list(CodeProvider.GITHUB, "octocat")

        assertEquals(listOf(hello), assertIs<RepositoryListing.Found>(again).repositories)
        assertEquals(1, fake.asked.size)
    }

    @Test
    fun `failures are not cached`() {
        val fake = FakeProvider(answer = RepositoryListing.RateLimited)
        val catalog = DefaultRepositoryCatalog(listOf(fake))

        assertEquals(RepositoryListing.RateLimited, catalog.list(CodeProvider.GITHUB, "octocat"))
        fake.answer = RepositoryListing.Found(listOf(hello))
        assertIs<RepositoryListing.Found>(catalog.list(CodeProvider.GITHUB, "octocat"))
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun `not found and unavailable answers are not cached either`() {
        val fake = FakeProvider(answer = RepositoryListing.NotFound)
        val catalog = DefaultRepositoryCatalog(listOf(fake))

        catalog.list(CodeProvider.GITHUB, "ghost")
        fake.answer = RepositoryListing.Unavailable
        catalog.list(CodeProvider.GITHUB, "ghost")
        assertEquals(2, fake.asked.size)
    }

    @Test
    fun `a provider is selected by its value`() {
        val fake = FakeProvider(answer = RepositoryListing.Found(listOf(hello)))
        val catalog = DefaultRepositoryCatalog(listOf(fake))

        assertIs<RepositoryListing.Found>(catalog.list(CodeProvider.GITHUB, "octocat"))
        assertEquals(listOf("octocat"), fake.asked)
    }

    @Test
    fun `two providers for the same value are refused at start-up`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultRepositoryCatalog(
                listOf(
                    FakeProvider(answer = RepositoryListing.NotFound),
                    FakeProvider(answer = RepositoryListing.NotFound),
                ),
            )
        }
    }
}
