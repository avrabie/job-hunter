package net.s4v3.backend

import net.s4v3.backend.sources.internal.FetchStateStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@IntegrationTest
internal class FetchStateStoreTests(@Autowired private val store: FetchStateStore) {

    private fun id() = "greenhouse-" + UUID.randomUUID()

    @Test
    fun `an unknown board has no state`() {
        val id = id()

        assertNull(store.lastRunAt(id))
        assertNull(store.etag(id))
    }

    @Test
    fun `the validator survives a later run start and is read back`() {
        val id = id()
        store.recordRunStart(id, Instant.now())
        store.saveEtag(id, "W/\"v1\"")

        store.recordRunStart(id, Instant.now())

        assertEquals("W/\"v1\"", store.etag(id))
    }

    @Test
    fun `the interval is measured from the last run start`() {
        val id = id()
        val first = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(600)
        store.recordRunStart(id, first)
        assertEquals(first, store.lastRunAt(id))

        val second = first.plusSeconds(300)
        store.recordRunStart(id, second)

        assertEquals(second, store.lastRunAt(id))
    }
}
