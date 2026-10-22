package net.s4v3.backend

import net.s4v3.backend.sources.internal.RobotsTxt
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotsTxtTests {

    @Test
    fun `greenhouse rules allow the board API and block embeds`() {
        val robots = RobotsTxt.parse("User-agent: *\nDisallow: /embed/\n")

        assertTrue(robots.allows("/v1/boards/proton/jobs"))
        assertFalse(robots.allows("/embed/job_board"))
    }

    @Test
    fun `a disallowed path is blocked and others are allowed`() {
        val robots = RobotsTxt.parse("User-agent: *\nDisallow: /v1/boards/acme/\n")

        assertFalse(robots.allows("/v1/boards/acme/jobs"))
        assertTrue(robots.allows("/v1/boards/other/jobs"))
    }

    @Test
    fun `wildcard and end anchor`() {
        val robots = RobotsTxt.parse("User-agent: *\nDisallow: /*.txt$\n")

        assertFalse(robots.allows("/a/b.txt"))
        assertTrue(robots.allows("/a/b.txt.html"))
        assertTrue(robots.allows("/v1/boards/acme/jobs"))
    }

    @Test
    fun `the longest match wins and allow wins a tie`() {
        val robots = RobotsTxt.parse("User-agent: *\nDisallow: /v1/\nAllow: /v1/boards/\nDisallow: /same\nAllow: /same\n")

        assertTrue(robots.allows("/v1/boards/acme/jobs"))
        assertFalse(robots.allows("/v1/other"))
        assertTrue(robots.allows("/same"))
    }

    @Test
    fun `an empty Disallow allows everything`() {
        assertTrue(RobotsTxt.parse("User-agent: *\nDisallow:\n").allows("/anything"))
    }

    @Test
    fun `only other agents' groups means everything is allowed`() {
        val robots = RobotsTxt.parse("User-agent: GPTBot\nDisallow: /\n\nUser-agent: Googlebot\nDisallow: /v1/\n")

        assertTrue(robots.allows("/v1/boards/acme/jobs"))
    }

    @Test
    fun `comments, blank lines and field name case are handled`() {
        val robots = RobotsTxt.parse("# note\n\nUSER-AGENT: bot\nUSER-AGENT: *   # both\nDISALLOW: /x # hidden\n")

        assertFalse(robots.allows("/x/y"))
        assertTrue(robots.allows("/y"))
    }
}
