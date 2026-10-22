package net.s4v3.backend

import net.s4v3.backend.postings.internal.Normalization
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class NormalizationTests {

    @Test
    fun `wording noise does not change the dedupe key`() {
        val a = Normalization.dedupeKey("Acme AG", "Senior Kotlin Developer (m/w/d)", "Zürich")
        val b = Normalization.dedupeKey("  ACME  AG ", "senior kotlin developer", "Zurich")
        val c = Normalization.dedupeKey("Acme AG", "Senior Kotlin-Developer (m/f/d)", "ZURICH")
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun `a different city gives a different key`() {
        assertNotEquals(
            Normalization.dedupeKey("Acme AG", "Kotlin Developer", "Zurich"),
            Normalization.dedupeKey("Acme AG", "Kotlin Developer", "Bern"),
        )
    }

    @Test
    fun `a different company or title gives a different key`() {
        val base = Normalization.dedupeKey("Acme AG", "Kotlin Developer", "Zurich")
        assertNotEquals(base, Normalization.dedupeKey("Beta AG", "Kotlin Developer", "Zurich"))
        assertNotEquals(base, Normalization.dedupeKey("Acme AG", "Java Developer", "Zurich"))
    }

    @Test
    fun `a missing or blank city gives no key`() {
        assertNull(Normalization.dedupeKey("Acme AG", "Kotlin Developer", null))
        assertNull(Normalization.dedupeKey("Acme AG", "Kotlin Developer", "  "))
    }

    @Test
    fun `workload forms`() {
        assertEquals(80 to 100, Normalization.workload("80-100%"))
        assertEquals(80 to 100, Normalization.workload("Pensum 100 – 80 %"))
        assertEquals(100 to 100, Normalization.workload("100%"))
        assertNull(Normalization.workload(null))
        assertNull(Normalization.workload("flexible"))
        assertNull(Normalization.workload("250%"))
    }

    @Test
    fun `content hash follows the content`() {
        assertEquals(Normalization.contentHash("t", "c", "d"), Normalization.contentHash("t", "c", "d"))
        assertNotEquals(Normalization.contentHash("t", "c", "d"), Normalization.contentHash("t", "c", "d2"))
        assertNotEquals(Normalization.contentHash("ab", "c"), Normalization.contentHash("a", "bc"))
    }
}
