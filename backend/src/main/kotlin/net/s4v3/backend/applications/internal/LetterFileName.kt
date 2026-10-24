package net.s4v3.backend.applications.internal

import java.text.Normalizer

/** ASCII only, as the CV export's `slug`: accents are stripped, `ß` becomes `ss`, every other run of characters becomes one dash. */
internal fun slug(text: String, fallback: String, max: Int): String {
    val plain = Normalizer.normalize(text.replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    return plain.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(max).trim('-').ifEmpty { fallback }
}

/** `motivation-<company>-<title>.md`, at most 95 characters of `[a-z0-9.-]`. */
internal fun letterFileName(company: String, title: String): String =
    "motivation-${slug(company, "company", 40)}-${slug(title, "job", 40)}.md"
