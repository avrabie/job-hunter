package net.s4v3.backend.llm.internal

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the model is asked to return for a repository. The `@LLMDescription` texts become part of the JSON schema that Koog
 * sends to the model, so they are the instructions for each field.
 *
 * Categories are plain strings here on purpose: one unknown value must not make the whole answer unparseable. [AnalysisMapper]
 * turns them into the typed `TechCategory` (unknown becomes `OTHER`) and checks every evidence path against the tree.
 */
@Serializable
@SerialName("ProjectAnalysisDraft")
@LLMDescription(
    "What the repository shows. Use only what the files state. Never guess or infer technologies that no shown file supports.",
)
internal data class ProjectAnalysisDraft(
    @property:LLMDescription("A short summary of what the project is, in a few sentences, from the files.")
    val summary: String? = null,
    @property:LLMDescription("A short description of how the project is structured and built, from the files.")
    val architecture: String? = null,
    @property:LLMDescription("The technologies the files show, each with a category and the path of one shown file that shows it.")
    val techStack: List<TechnologyDraft> = emptyList(),
)

@Serializable
@SerialName("TechnologyDraft")
internal data class TechnologyDraft(
    @property:LLMDescription("Name of the technology, e.g. Spring Boot, PostgreSQL, React.")
    val name: String? = null,
    @property:LLMDescription("One of LANGUAGE, FRAMEWORK, LIBRARY, DATABASE, TOOL, PLATFORM, OTHER.")
    val category: String? = null,
    @property:LLMDescription("The exact path of one file shown to you that shows this technology, copied from the tree or the headings.")
    val evidencePath: String? = null,
    @property:LLMDescription("A short note on what in that file shows the technology.")
    val evidence: String? = null,
)
