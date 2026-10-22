package net.s4v3.backend.postings.internal

import java.security.MessageDigest
import java.text.Normalizer

/** Normalization behind cross-source dedupe and change detection. Pure functions, no state. */
internal object Normalization {

    private val diacritics = Regex("\\p{M}+")
    private val genderMarker = Regex("""\((?:[mwfdxi*]\s*[/|]\s*)+[mwfdxi*]\)|\ball genders\b|\b[mwfdx]/[mwfdx]/[mwfdx]\b""")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")
    private val workloadRange = Regex("""(\d{1,3})\s*(?:-|–|to|bis)\s*(\d{1,3})\s*%""")
    private val workloadSingle = Regex("""(\d{1,3})\s*%""")

    fun text(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFKD)
        val plain = diacritics.replace(decomposed, "")
        return nonAlphanumeric.replace(genderMarker.replace(plain, " "), " ").trim()
    }

    /** Null when the city is unknown: such a posting is only deduplicated against its own source. */
    fun dedupeKey(company: String, title: String, city: String?): String? {
        val normCity = city?.let(::text)?.takeIf { it.isNotEmpty() } ?: return null
        return sha256(listOf(text(company), text(title), normCity).joinToString("|"))
    }

    fun contentHash(vararg parts: Any?): String = sha256(parts.joinToString("\u0000") { it?.toString().orEmpty() })

    /** "80-100%" gives 80..100, "100%" gives 100..100, anything else is unknown. */
    fun workload(value: String?): Pair<Int, Int>? {
        if (value == null) return null
        workloadRange.find(value)?.let { m ->
            val (a, b) = m.groupValues[1].toInt() to m.groupValues[2].toInt()
            return validWorkload(minOf(a, b), maxOf(a, b))
        }
        workloadSingle.find(value)?.let { m -> m.groupValues[1].toInt().let { return validWorkload(it, it) } }
        return null
    }

    private fun validWorkload(min: Int, max: Int) = if (min in 1..100 && max in 1..100) min to max else null

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
