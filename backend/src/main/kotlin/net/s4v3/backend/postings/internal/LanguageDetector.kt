package net.s4v3.backend.postings.internal

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import org.springframework.stereotype.Component

/** Result of [LanguageDetector.detect]: a code stored on the posting plus whether a person should look at it. */
internal data class Detected(val code: String, val needsReview: Boolean) {
    /** French, Italian and undetected postings are stored but not drafted. */
    val outOfScope: Boolean get() = code !in DRAFTABLE

    companion object {
        val DRAFTABLE = setOf("en", "de")
    }
}

/**
 * Detects en/de/fr/it from the title and the start of the description. Text that is too short, or a result that is not
 * clearly ahead of the runner-up, becomes `other` and is flagged for review instead of being guessed.
 */
@Component
internal class LanguageDetector {

    // Built lazily: loading the four language models takes a moment and memory, and unit tests that never detect skip it.
    private val detector by lazy {
        LanguageDetectorBuilder
            .fromLanguages(Language.ENGLISH, Language.GERMAN, Language.FRENCH, Language.ITALIAN)
            .build()
    }

    fun detect(title: String, description: String): Detected {
        val text = "$title. ${description.take(MAX_CHARS)}".trim()
        if (text.length < MIN_CHARS) return Detected("other", needsReview = true)

        val top = detector.computeLanguageConfidenceValues(text).entries.take(2)
        val best = top.firstOrNull() ?: return Detected("other", needsReview = true)
        val margin = best.value - (top.getOrNull(1)?.value ?: 0.0)
        if (margin < MIN_MARGIN) return Detected("other", needsReview = true)

        val code = when (best.key) {
            Language.ENGLISH -> "en"
            Language.GERMAN -> "de"
            Language.FRENCH -> "fr"
            Language.ITALIAN -> "it"
            else -> return Detected("other", needsReview = true)
        }
        return Detected(code, needsReview = false)
    }

    private companion object {
        const val MAX_CHARS = 2000
        const val MIN_CHARS = 40
        // Lingua scores are relative to the best candidate (best is always 1.0), so only the gap to the runner-up counts.
        const val MIN_MARGIN = 0.08
    }
}
