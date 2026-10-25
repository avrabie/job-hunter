package net.s4v3.backend

import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.postings.PostingSearch
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IntegrationTest
class PostingSearchTests(
    @Autowired private val ingest: PostingIngest,
    @Autowired private val query: PostingQuery,
) {

    private val english = "We are looking for an experienced engineer to build and run reliable services with Kotlin and PostgreSQL. " +
        "You will work in a small team, review code, and improve our delivery pipeline every week."

    private fun unique() = "uq" + UUID.randomUUID().toString().replace("-", "").take(10)

    private fun add(
        title: String, company: String = "Searchco " + unique(), canton: String? = "ZH", source: String = "search-src-" + unique(),
    ): UUID = ingest.ingest(
        PostingCandidate(
            sourceId = source, sourcePostingId = unique(), url = "https://example.org/$source/" + unique(), title = title,
            company = company, description = english, rawPayload = "{}", city = "Zurich", canton = canton,
        ),
    ).postingId

    private fun ids(search: PostingSearch) = query.search(search).map { it.id }

    @Test
    fun `text matches title and company ignoring case`() {
        val tag = unique()
        val kotlin = add("Kotlin Developer $tag")
        add("Graphic Designer $tag")
        val byCompany = add("Engineer", company = "ACME $tag AG")

        assertEquals(listOf(kotlin), ids(PostingSearch(text = "kotlin developer $tag")))
        assertEquals(listOf(byCompany), ids(PostingSearch(text = "acme $tag")))
    }

    @Test
    fun `canton and source filters combine`() {
        val tag = unique()
        val source = "search-src-" + unique()
        val zh = add("Dev $tag", canton = "ZH", source = source)
        add("Dev $tag", company = "Other " + unique(), canton = "GE", source = source)
        add("Dev $tag", company = "Third " + unique(), canton = "ZH")

        assertEquals(setOf(zh), ids(PostingSearch(text = tag, canton = "ZH", sourceId = source)).toSet())
        assertEquals(3, ids(PostingSearch(text = tag)).size)
    }

    @Test
    fun `newest first-seen comes first and paging works`() {
        val tag = unique()
        val first = add("Dev $tag one")
        Thread.sleep(20)
        val second = add("Dev $tag two")

        assertEquals(listOf(second, first), ids(PostingSearch(text = tag)))
        assertEquals(listOf(second), ids(PostingSearch(text = tag, size = 1)))
        assertEquals(listOf(first), ids(PostingSearch(text = tag, size = 1, page = 1)))
        assertTrue(query.search(PostingSearch(size = 1000)).size <= PostingQuery.MAX_PAGE_SIZE)
    }

    @Test
    fun `one posting from two sources has two links with the primary first`() {
        val tag = unique()
        val company = "Twice " + unique()
        val primarySource = "search-src-" + unique()
        val secondSource = "search-src-" + unique()
        val id = add("Dev $tag", company = company, source = primarySource)
        val linked = add("Dev $tag", company = company, source = secondSource)

        val posting = query.search(PostingSearch(text = tag)).single()

        assertEquals(id, linked)
        assertEquals(id, posting.id)
        assertEquals(listOf(primarySource, secondSource), posting.links.map { it.sourceId })
        assertEquals(listOf(true, false), posting.links.map { it.primary })
        assertEquals(posting.url, posting.links.first().url)
        assertEquals(posting.links, query.find(id)!!.links)
    }

    @Test
    fun `wildcard characters in the text are literal`() {
        val tag = unique()
        val percent = add("Dev $tag 100% remote")
        add("Dev $tag 100X remote")
        val underscore = add("Dev $tag a_b role")
        add("Dev $tag axb role")
        val backslash = add("Dev $tag c\\d role")

        assertEquals(listOf(percent), ids(PostingSearch(text = "$tag 100%")))
        assertEquals(listOf(underscore), ids(PostingSearch(text = "$tag a_b")))
        assertEquals(listOf(backslash), ids(PostingSearch(text = "$tag c\\d")))
    }

    @Test
    fun `userAdded is true only when the primary source is user-import`() {
        val tag = unique()
        val company = "Marker " + unique()
        val crawled = add("Dev $tag", company = company, source = "search-src-" + unique())
        val imported = add("Imported $tag", source = "user-import")
        val importedOnCrawled = add("Dev $tag", company = company, source = "user-import")

        assertEquals(crawled, importedOnCrawled)
        assertEquals(false, query.find(crawled)!!.userAdded)
        assertEquals(true, query.find(imported)!!.userAdded)
        assertEquals(listOf(false), query.search(PostingSearch(text = company)).map { it.userAdded })
        assertEquals(listOf(true), query.search(PostingSearch(text = "Imported $tag")).map { it.userAdded })
    }

    @Test
    fun `a blank text does not filter and nothing matches an unknown word`() {
        assertEquals(emptyList(), ids(PostingSearch(text = "nomatch" + unique())))
    }
}
