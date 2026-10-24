package net.s4v3.backend.cv.export.internal

/** The only way profile text enters markup: escapes `& < > " '`. */
internal fun esc(text: String): String = buildString(text.length + 16) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

/** True only for http and https addresses; everything else is printed as plain text. */
internal fun isWebUrl(url: String?): Boolean {
    if (url == null) return false
    val u = url.trim().lowercase()
    return (u.startsWith("http://") || u.startsWith("https://")) && u.none { it.isWhitespace() }
}
