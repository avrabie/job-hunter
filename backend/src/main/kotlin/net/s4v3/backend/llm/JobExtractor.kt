package net.s4v3.backend.llm

/**
 * Turns the text of a web page into a job draft. The page text is untrusted data. Callers pass plain text and get a plain
 * result, and never see Koog, prompts or model answers.
 */
interface JobExtractor {
    /** Throws [LlmFailure] with a fixed message when the model cannot be used or its answer is not usable. */
    fun extract(pageText: String): JobDraft
}

/**
 * What the model found. When [found] is false the other fields are empty. [description] is plain text copied from the page;
 * it is not yet checked against the page (the caller does that).
 */
data class JobDraft(
    val found: Boolean,
    val title: String,
    val company: String,
    val location: String?,
    val description: String,
    val language: LetterLanguage,
)
