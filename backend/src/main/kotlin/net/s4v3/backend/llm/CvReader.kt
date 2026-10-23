package net.s4v3.backend.llm

import net.s4v3.backend.profile.Profile

/**
 * Reads the facts out of CV text with a language model. The whole language-model side of the application is behind this
 * one call: callers pass text and get a typed [Profile] draft back, and never see Koog, prompts or model answers.
 */
fun interface CvReader {
    /** Throws [LlmFailure] with a fixed message when the model cannot be used or its answer is not usable. */
    fun profileFrom(text: String): Profile
}

enum class LlmFailureKind { NOT_CONFIGURED, FAILED }

/** The only error [CvReader] reports. The message is fixed text: it never holds CV text, a prompt or a model answer. */
class LlmFailure(val kind: LlmFailureKind) : RuntimeException(
    when (kind) {
        LlmFailureKind.NOT_CONFIGURED -> "The language model is not configured."
        LlmFailureKind.FAILED -> "The language model could not produce a usable answer."
    },
)
