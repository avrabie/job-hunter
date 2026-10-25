package net.s4v3.backend.llm.internal

import net.s4v3.backend.profile.Contact
import net.s4v3.backend.profile.Profile
import java.net.URI
import java.net.URISyntaxException

/**
 * Keeps a contact value only if the CV text contains it, so the model cannot invent an address or number and text hidden
 * in the CV cannot plant one. Values are also shaped by the rules the profile module applies on Save (a small copy, because
 * its validator is internal to that module): what the server would refuse is dropped here. Dropped values become null
 * without a trace; nothing is logged.
 */
internal object ContactGrounding {

    fun ground(draft: Profile, cvText: String): Profile {
        val c = draft.contact
        val lower = cvText.lowercase()
        return draft.copy(
            contact = Contact(
                fullName = name(c.fullName, lower),
                headline = c.headline?.takeIf { it.length <= TEXT_MAX },
                email = email(c.email, lower),
                phone = phone(c.phone, cvText),
                website = link(c.website, lower),
                linkedin = link(c.linkedin, lower),
            ),
        )
    }

    private fun name(value: String?, lowerText: String): String? {
        val trimmed = value?.trim() ?: return null
        val words = trimmed.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty() || trimmed.length > TEXT_MAX) return null
        return trimmed.takeIf { words.all { it in lowerText } }
    }

    private fun email(value: String?, lowerText: String): String? =
        value?.trim()?.takeIf { it.length <= TEXT_MAX && EMAIL.matches(it) && it.lowercase() in lowerText }

    /** The digits (a leading `00` ignored; the `+` is not a digit) must be a contiguous run in the CV's digits. */
    private fun phone(value: String?, cvText: String): String? {
        val phone = value?.trim() ?: return null
        if (phone.length > PHONE_MAX || !PHONE_CHARACTERS.matches(phone)) return null
        val digits = phone.filter(Char::isDigit).removePrefix("00")
        if (digits.isEmpty()) return null
        return phone.takeIf { digits in cvText.filter(Char::isDigit) }
    }

    /** Host and path must occur in the text without scheme, `www.` and a trailing slash; the result is an absolute https address. */
    private fun link(value: String?, lowerText: String): String? {
        val bare = value?.trim()?.replace(SCHEME, "")?.removePrefix("www.")?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        val url = "https://$bare"
        if (url.length > URL_MAX || bare.lowercase() !in lowerText) return null
        return try {
            url.takeIf { !URI(it).host.isNullOrEmpty() }
        } catch (_: URISyntaxException) {
            null
        }
    }

    private const val TEXT_MAX = 200
    private const val PHONE_MAX = 30
    private const val URL_MAX = 500
    private val WHITESPACE = Regex("\\s+")
    private val SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    private val PHONE_CHARACTERS = Regex("[0-9 +()/-]+")
}
