package net.s4v3.backend.llm.internal

/** Cleans untrusted text before it goes between the delimiter tags of a prompt. */
internal object PromptText {
    /** A tag-like run: `<`, optional spaces, optional `/`, a letter, up to the next `>` (any case, any spacing). */
    private val TAG = Regex("<\\s*/?\\s*[A-Za-z_][^<>]*>?")

    /** A `<` that is still followed by a letter or `/` after tags are gone (for example an unclosed one). */
    private val LONE = Regex("<(?=\\s*[/A-Za-z_])")

    /** [text] without any angle-bracket tag, so it can neither close a delimiter nor open a new one. */
    fun strip(text: String): String = text.replace(TAG, "").replace(LONE, "")
}
