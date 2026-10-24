package net.s4v3.backend.llm

import net.s4v3.backend.profile.ProjectAnalysis
import net.s4v3.backend.profile.RepositoryDigest

/**
 * Describes one repository with a language model: what it is, how it is built and which technologies its files show. Callers
 * pass the digest and get a checked [ProjectAnalysis] back, and never see Koog, prompts or model answers.
 */
interface ProjectAnalyst {
    /** Throws [LlmFailure] with a fixed message when the model cannot be used or its answer is not usable. */
    fun analyze(digest: RepositoryDigest): ProjectAnalysis
}
