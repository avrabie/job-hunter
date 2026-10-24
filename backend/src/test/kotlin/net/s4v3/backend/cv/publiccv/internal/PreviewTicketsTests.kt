package net.s4v3.backend.cv.publiccv.internal

import net.s4v3.backend.TestClock
import net.s4v3.backend.cv.publiccv.PublicCvSettings
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class PreviewTicketsTests {

    private val clock = TestClock()
    private val tickets = PreviewTickets(clock)
    private val settings = PublicCvSettings(headline = true)

    @Test
    fun `a ticket is 128 random bits as hex and differs each time`() {
        val a = tickets.issue("u", settings)
        val b = tickets.issue("u", settings)
        assertEquals(32, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertNotEquals(a, b)
    }

    @Test
    fun `a ticket is bound to its owner and settings`() {
        val ticket = tickets.issue("owner-1", settings)
        assertEquals(PreviewBinding("owner-1", settings), tickets.redeem(ticket))
    }

    @Test
    fun `a ticket works once`() {
        val ticket = tickets.issue("u", settings)
        assertEquals("u", tickets.redeem(ticket)?.sub)
        assertNull(tickets.redeem(ticket))
    }

    @Test
    fun `a ticket works for 60 seconds`() {
        val ticket = tickets.issue("u", settings)
        clock.advance(Duration.ofSeconds(59))
        assertEquals("u", tickets.redeem(ticket)?.sub)
        val late = tickets.issue("u", settings)
        clock.advance(Duration.ofSeconds(60))
        assertNull(tickets.redeem(late))
    }

    @Test
    fun `an invented ticket answers null`() {
        assertNull(tickets.redeem("0".repeat(32)))
        assertNull(tickets.redeem(""))
    }

    @Test
    fun `expired tickets are swept when a ticket is issued, not when one is redeemed`() {
        tickets.issue("u", settings)
        clock.advance(Duration.ofSeconds(61))

        assertNull(tickets.redeem("0".repeat(32)))
        assertEquals(1, tickets.size())

        tickets.issue("u", settings)
        assertEquals(1, tickets.size())
    }
}
