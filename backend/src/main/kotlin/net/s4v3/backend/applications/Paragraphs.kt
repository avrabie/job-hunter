package net.s4v3.backend.applications

private val BLANK_LINES = Regex("(?:\\r?\\n[^\\S\\r\\n]*){2,}")

/**
 * The paragraph rule of the letters (design decision 10): split on one or more blank lines (a line with only whitespace is
 * blank), trim each part, drop empty parts. The UI has the same function; `ui/src/applications/paragraphs.cases.json` is the
 * case table both sides test against.
 */
fun paragraphs(text: String): List<String> = text.split(BLANK_LINES).map { it.trim() }.filter { it.isNotEmpty() }
