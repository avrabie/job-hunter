package net.s4v3.backend.cv.publiccv.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The handle rules without the database (`cv-public` "Handle"). */
internal class HandleRulesTests {

    @Test
    fun `input is lower-cased before it is stored`() {
        assertEquals("ada-example", HandleRules.normalize("Ada-Example"))
        assertNull(HandleRules.validate("Ada-Example"))
    }

    @Test
    fun `valid forms are accepted`() {
        listOf("abc", "a-b", "ada-example", "x1-2-3", "123abc", "12-34", "a".repeat(30)).forEach { handle ->
            assertNull(HandleRules.validate(handle), handle)
        }
    }

    @Test
    fun `invalid forms are refused`() {
        listOf("ab", "-ada", "ada-", "ada--x", "ada_x", "ada x", "a".repeat(31), "123456", "", "äda").forEach { handle ->
            assertEquals(HandleProblem.INVALID, HandleRules.validate(handle), handle)
        }
    }

    @Test
    fun `reserved handles are refused whatever their capitalisation`() {
        assertEquals(HandleProblem.RESERVED, HandleRules.validate("admin"))
        assertEquals(HandleProblem.RESERVED, HandleRules.validate("Profile"))
        assertEquals(HandleProblem.RESERVED, HandleRules.validate("S4V3"))
    }

    @Test
    fun `the limits are three and thirty characters`() {
        assertEquals(3, HandleRules.MIN_LENGTH)
        assertEquals(30, HandleRules.MAX_LENGTH)
    }
}
