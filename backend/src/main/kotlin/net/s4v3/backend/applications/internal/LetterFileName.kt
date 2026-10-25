package net.s4v3.backend.applications.internal

import java.text.Normalizer

/** ASCII only, as the CV export's `slug`: accents are stripped, `ß` becomes `ss`, every other run of characters becomes one dash. */
internal fun slug(text: String, fallback: String, max: Int): String {
    val plain = Normalizer.normalize(text.replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    return plain.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(max).trim('-').ifEmpty { fallback }
}

/**
 * `motivation-<company>-<title>-v<version>.md`, at most 100 characters of `[a-z0-9.-]`. The version is in the name so that
 * two versions never share a file name (browsers would otherwise save the second as "name (1).md").
 */
internal fun letterFileName(company: String, title: String, version: Int): String =
    "motivation-${slug(company, "company", 40)}-${slug(title, "job", 40)}-v$version.md"
