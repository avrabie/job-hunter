package net.s4v3.backend.jobimport.internal

import net.s4v3.backend.jobimport.ImportContact
import net.s4v3.backend.llm.JobDraft

/**
 * Keeps an extracted company address or contact value only if the page text contains it, so the model cannot invent one. A
 * value the page does not contain (or of the wrong shape) becomes null without a trace: it never fails the import and nothing
 * is logged. A value planted in the page passes, which is why the user reviews every value before confirming.
 */
internal object JobContactGrounding {

    fun ground(draft: JobDraft, pageText: String): ImportContact {
        val lower = pageText.lowercase()
        val words = WORD.findAll(lower).map { it.value }.toHashSet()
        return ImportContact(
            street = text(draft.street, words),
            postalCode = postalCode(draft.postalCode, pageText),
            city = text(draft.city, words),
            person = text(draft.contactPerson, words),
            phone = phone(draft.contactPhone, pageText),
            email = email(draft.contactEmail, lower),
        )
    }

    /** Every word of the value must be a word of the page. */
    private fun text(value: String?, pageWords: Set<String>): String? {
        val trimmed = value?.trim() ?: return null
        val wanted = WORD.findAll(trimmed.lowercase()).map { it.value }.toList()
        if (wanted.isEmpty() || trimmed.length > TEXT_MAX) return null
        return trimmed.takeIf { wanted.all { it in pageWords } }
    }

    private fun email(value: String?, lowerText: String): String? =
        value?.trim()?.takeIf { it.length <= EMAIL_MAX && EMAIL.matches(it) && it.lowercase() in lowerText }

    /** At least 7 digits; the value must occur in the page once spaces, dots, dashes, slashes and brackets are left out of both. */
    private fun phone(value: String?, pageText: String): String? {
        val phone = value?.trim() ?: return null
        if (phone.length > PHONE_MAX || !PHONE_CHARACTERS.matches(phone) || phone.count(Char::isDigit) < PHONE_MIN_DIGITS) return null
        return phone.takeIf { PHONE_SEPARATORS.replace(phone, "") in PHONE_SEPARATORS.replace(pageText, "") }
    }

    /** 4 or 5 digits that occur as a number of their own, not inside a longer one. */
    private fun postalCode(value: String?, pageText: String): String? {
        val code = value?.trim() ?: return null
        if (!POSTAL_CODE.matches(code)) return null
        return code.takeIf { Regex("(?<![0-9])$code(?![0-9])").containsMatchIn(pageText) }
    }

    private const val TEXT_MAX = 200
    private const val EMAIL_MAX = 254
    private const val PHONE_MAX = 40
    private const val PHONE_MIN_DIGITS = 7
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    private val PHONE_CHARACTERS = Regex("[0-9 +()/.-]+")
    private val PHONE_SEPARATORS = Regex("[\\s.\\-/()]")
    private val POSTAL_CODE = Regex("[0-9]{4,5}")
}
