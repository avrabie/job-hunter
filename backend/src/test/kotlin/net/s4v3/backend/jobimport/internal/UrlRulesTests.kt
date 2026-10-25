package net.s4v3.backend.jobimport.internal

import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlRulesTests {

    private fun rejected(url: String): FetchError? = (UrlRules.check(url) as? UrlCheck.Rejected)?.error

    private fun canonical(url: String): String = (UrlRules.check(url) as UrlCheck.Ok).url.canonical

    @Test
    fun `accepts a plain https url`() {
        val ok = UrlRules.check("https://Jobs.Example.com/ada-engineer") as UrlCheck.Ok
        assertEquals("jobs.example.com", ok.url.host)
        assertEquals("/ada-engineer", ok.url.target)
    }

    @Test
    fun `rejects http, other ports, userinfo, ip literals and junk`() {
        listOf(
            "http://jobs.example.com/a",
            "https://203.0.113.5/a",
            "https://[::1]/a",
            "https://2130706433/a",
            "https://0x7f.0.0.1/a",
            "https://jobs.example.com:8443/a",
            "https://ada@jobs.example.com/a",
            "https://localhost/a",
            "ftp://jobs.example.com/a",
            "not a url",
            "",
            "https://jobs.example.com/" + "a".repeat(2100),
        ).forEach { assertEquals(FetchError.INVALID_URL, rejected(it), "should be invalid") }
    }

    @Test
    fun `accepts the explicit port 443`() {
        assertNull(rejected("https://jobs.example.com:443/a"))
    }

    @Test
    fun `refuses the sites the project does not scrape`() {
        listOf(
            "https://linkedin.com/jobs/1",
            "https://www.LinkedIn.com/jobs/1",
            "https://de.indeed.com/viewjob?jk=1",
            "https://indeed.com/",
        ).forEach { assertEquals(FetchError.UNSUPPORTED_SITE, rejected(it)) }
        assertNull(rejected("https://notlinkedin.com/jobs"))
    }

    @Test
    fun `canonical form drops fragment, tracking parameters and trailing slash`() {
        val expected = "https://jobs.example.com/ada-engineer?id=7"
        assertEquals(expected, canonical("https://jobs.example.com/ada-engineer?id=7"))
        assertEquals(expected, canonical("https://JOBS.example.com/ada-engineer/?id=7&utm_source=x&gclid=1&fbclid=2#apply"))
        assertEquals("https://jobs.example.com/ada-engineer", canonical("https://jobs.example.com/ada-engineer/?utm_medium=m"))
        assertEquals("https://jobs.example.com", canonical("https://jobs.example.com/"))
    }

    @Test
    fun `target keeps the path for the request`() {
        val ok = UrlRules.check("https://jobs.example.com?utm_source=x#top") as UrlCheck.Ok
        assertEquals("/", ok.url.target)
    }
}

class AddressRulesTests {

    private fun public(ip: String) = AddressRules.isPublic(InetAddress.getByName(ip))

    @Test
    fun `public addresses pass`() {
        listOf("93.184.216.34", "8.8.8.8", "1.1.1.1", "2606:2800:220:1:248:1893:25c8:1946", "2001:4860:4860::8888")
            .forEach { assertTrue(public(it), it) }
    }

    @Test
    fun `internal addresses fail`() {
        listOf(
            "127.0.0.1", "127.8.8.8", "0.0.0.0", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1",
            "169.254.169.254", "169.254.0.1", "100.64.0.1", "100.127.255.255", "224.0.0.1", "239.255.255.255",
            "255.255.255.255", "192.0.0.8", "198.18.0.1",
            "::", "::1", "fc00::1", "fd12:3456::1", "fe80::1", "fec0::1", "ff02::1",
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254", "64:ff9b::a00:1",
        ).forEach { assertFalse(public(it), it) }
    }

    @Test
    fun `edges of the ranges are public`() {
        listOf("172.15.255.255", "172.32.0.1", "100.63.255.255", "100.128.0.1", "198.20.0.1", "::ffff:8.8.8.8")
            .forEach { assertTrue(public(it), it) }
    }
}
