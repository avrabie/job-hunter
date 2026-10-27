package net.s4v3.backend

import net.s4v3.backend.postings.PostingCandidate
import net.s4v3.backend.postings.PostingIngest
import net.s4v3.backend.postings.PostingQuery
import net.s4v3.backend.postings.PostingSearch
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@IntegrationTest
class PostingSearchTests(
    @Autowired private val ingest: PostingIngest,
    @Autowired private val query: PostingQuery,
    @Autowired private val jdbc: JdbcTemplate,
) {

    private fun seen(id: UUID, at: Instant) {
        jdbc.update("UPDATE posting SET first_seen = ? WHERE id = ?", Timestamp.from(at), id)
    }

    private fun language(id: UUID, code: String) {
        jdbc.update("UPDATE posting SET language = ? WHERE id = ?", code, id)
    }


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
    fun `a blank text does not filter and nothing matches an unknown word`() {
        assertEquals(emptyList(), ids(PostingSearch(text = "nomatch" + unique())))
    }

    @Test
    fun `first-seen bounds include the lower and exclude the upper bound, and count matches`() {
        val tag = unique()
        val base = Instant.parse("2020-03-10T12:00:00Z")
        val before = add("Dev $tag a").also { seen(it, base.minusSeconds(1)) }
        val atFrom = add("Dev $tag b").also { seen(it, base) }
        val inside = add("Dev $tag c").also { seen(it, base.plusSeconds(3600)) }
        val atBefore = add("Dev $tag d").also { seen(it, base.plusSeconds(7200)) }
        val window = PostingSearch(text = tag, firstSeenFrom = base, firstSeenBefore = base.plusSeconds(7200))

        assertEquals(listOf(inside, atFrom), ids(window))
        assertEquals(2, query.count(window))
        assertEquals(4, query.count(PostingSearch(text = tag)))
        assertEquals(listOf(atBefore, inside, atFrom), ids(PostingSearch(text = tag, firstSeenFrom = base)))
        assertEquals(listOf(before), ids(PostingSearch(text = tag, firstSeenBefore = base)))
        assertEquals(4, query.count(PostingSearch(text = tag, size = 1, page = 3)))
    }

    @Test
    fun `postings with one first-seen time are ordered by id so pages neither skip nor repeat`() {
        val tag = unique()
        val at = Instant.parse("2020-04-01T08:00:00Z")
        val all = (1..5).map { add("Dev $tag $it").also { id -> seen(id, at) } }

        val paged = (0..2).flatMap { ids(PostingSearch(text = tag, size = 2, page = it)) }

        // Postgres compares uuids bytewise (unsigned), which is the order of their hex strings, not of UUID.compareTo.
        assertEquals(all.map { it.toString() }.sorted(), paged.map { it.toString() })
    }

    @Test
    fun `canton and language filters apply to the list and the count`() {
        val tag = unique()
        val zhDe = add("Dev $tag", canton = "ZH").also { language(it, "de") }
        add("Dev $tag", company = "O " + unique(), canton = "ZH").also { language(it, "en") }
        add("Dev $tag", company = "P " + unique(), canton = "GE").also { language(it, "de") }
        val search = PostingSearch(text = tag, canton = "ZH", language = "de")

        assertEquals(listOf(zhDe), ids(search))
        assertEquals(1, query.count(search))
        assertEquals(2, query.count(PostingSearch(text = tag, language = "de")))
    }
}
