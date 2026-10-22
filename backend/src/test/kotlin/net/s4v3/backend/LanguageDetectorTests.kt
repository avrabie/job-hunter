package net.s4v3.backend

import net.s4v3.backend.postings.internal.LanguageDetector
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageDetectorTests {

    private val detector = LanguageDetector()

    @Test
    fun `german posting`() {
        val d = detector.detect(
            "Softwareentwickler Kotlin",
            "Für unser Team in Zürich suchen wir eine erfahrene Softwareentwicklerin oder einen erfahrenen " +
                "Softwareentwickler, der mit uns moderne Backend-Systeme baut und betreibt.",
        )
        assertEquals("de", d.code)
        assertFalse(d.needsReview)
        assertFalse(d.outOfScope)
    }

    @Test
    fun `english posting`() {
        val d = detector.detect(
            "Senior Kotlin Developer",
            "We are looking for an experienced backend engineer to join our team and help us build and operate " +
                "reliable services for our customers across Europe.",
        )
        assertEquals("en", d.code)
        assertFalse(d.outOfScope)
    }

    @Test
    fun `french posting is kept and flagged out of scope`() {
        val d = detector.detect(
            "Développeur Kotlin",
            "Nous recherchons un développeur expérimenté pour rejoindre notre équipe à Genève et construire des " +
                "systèmes fiables pour nos clients dans toute la Suisse romande.",
        )
        assertEquals("fr", d.code)
        assertTrue(d.outOfScope)
    }

    @Test
    fun `too little text is other and flagged for review`() {
        val d = detector.detect("Dev", "Kotlin job")
        assertEquals("other", d.code)
        assertTrue(d.needsReview)
        assertTrue(d.outOfScope)
    }

    @Test
    fun `german posting with english tech terms is still german`() {
        val d = detector.detect(
            "Backend Engineer (m/w/d)",
            "Als Backend Engineer entwickelst du bei uns Microservices mit Kotlin und Spring Boot, betreibst sie " +
                "auf Kubernetes und arbeitest eng mit dem Product Team zusammen. Wir bieten dir flexible Arbeitszeiten " +
                "und ein starkes Team in Zürich.",
        )
        assertEquals("de", d.code)
    }
}
