package net.s4v3.backend.llm.internal

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import net.s4v3.backend.llm.AskedEvidence
import net.s4v3.backend.llm.AskedSkill
import net.s4v3.backend.llm.EvidenceKind
import net.s4v3.backend.llm.JobText
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import net.s4v3.backend.llm.SkillEvidenceInput
import net.s4v3.backend.llm.SkillExtractor
import net.s4v3.backend.llm.SkillImportance
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Serializable
@SerialName("AskedSkillDraft")
@LLMDescription("One skill the job advertisement asks for.")
internal data class AskedSkillDraft(
    @property:LLMDescription("Short name as the advertisement writes it.")
    val name: String = "",
    @property:LLMDescription("REQUIRED or NICE_TO_HAVE.")
    val importance: String = "REQUIRED",
    @property:LLMDescription("At most 3 names copied exactly from the saved skill names that are related to this skill.")
    val related: List<String> = emptyList(),
    @property:LLMDescription("Optional: {\"kind\":\"EXPERIENCE\" or \"PROJECT\",\"index\":number of the position or project,\"quote\":text copied exactly from it, at most 200 characters} for the one entry that shows the skill when it is not among the saved skills.")
    val evidence: JsonElement? = null,
)

@Serializable
@SerialName("SkillsDraft")
@LLMDescription("The skills a job advertisement asks for.")
internal data class SkillsDraft(
    @property:LLMDescription("At most 40 skills the advertisement names.")
    val skills: List<AskedSkillDraft> = emptyList(),
)

/**
 * [SkillExtractor] on Koog, in the same manual JSON structure mode as [KoogCvReader]. Uses `jobhunter.letters.model`, falling
 * back to `jobhunter.llm.model`. Nothing from the job, the skill names or the answer is logged.
 */
@Component
internal class KoogSkillExtractor(
    private val llm: LlmSettings,
    private val letters: LettersSettings,
    private val executors: ExecutorSource,
) : SkillExtractor {

    private val log = LoggerFactory.getLogger(KoogSkillExtractor::class.java)

    private val executor: PromptExecutor by lazy { executors.create(llm.apiKey) }

    override fun extract(job: JobText, evidenceInput: SkillEvidenceInput): List<AskedSkill> {
        if (llm.apiKey.isBlank()) throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val modelName = letters.model.ifBlank { llm.model }
        val model = KoogProjectAnalyst.modelFor(modelName) ?: throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val started = System.nanoTime()
        try {
            val draft = runBlocking {
                withTimeout(letters.timeout.toMillis()) {
                    executor.executeStructured(
                        prompt = prompt("job-skills", LLMParams(maxTokens = MAX_OUTPUT_TOKENS)) {
                            system(SkillPrompts.SYSTEM)
                            user(SkillPrompts.user(job, evidenceInput))
                        },
                        model = model,
                        config = StructuredRequestConfig(default = StructuredRequest.Manual(JsonStructure.create<SkillsDraft>())),
                        fixingParser = StructureFixingParser(model = AnthropicModels.Haiku_4_5, retries = 1),
                    ).getOrThrow().data
                }
            }
            val skills = clean(draft, evidenceInput)
            log.info("Skills extracted by the model in {} ms ({} skills)", elapsedMs(started), skills.size)
            return skills
        } catch (e: Exception) {
            // Only the class name: an exception message may carry the prompt or the answer.
            log.warn("Skill extraction failed after {} ms (model {}): {}", elapsedMs(started), modelName, e::class.simpleName)
            throw LlmFailure(LlmFailureKind.FAILED)
        }
    }

    /** Drops unusable entries and duplicates, caps the list; related names are kept only if exactly one of the given names. */
    private fun clean(draft: SkillsDraft, input: SkillEvidenceInput): List<AskedSkill> {
        val saved = input.savedSkillNames.toSet()
        val positions = input.positions.map { it.index }.toSet()
        val projects = input.projects.map { it.index }.toSet()
        val seen = HashSet<String>()
        return draft.skills.mapNotNull { s ->
            val name = s.name.trim()
            val importance = when (s.importance.trim().uppercase()) {
                "REQUIRED" -> SkillImportance.REQUIRED
                "NICE_TO_HAVE" -> SkillImportance.NICE_TO_HAVE
                else -> return@mapNotNull null
            }
            if (name.isEmpty() || name.length > SkillExtractor.MAX_NAME || HTML_TAG.containsMatchIn(name)) return@mapNotNull null
            if (!seen.add(name.lowercase())) return@mapNotNull null
            AskedSkill(name, importance, s.related.filter { it in saved }.distinct().take(SkillExtractor.MAX_RELATED), evidence(s.evidence, positions, projects))
        }.take(SkillExtractor.MAX_SKILLS).also { if (it.isEmpty() && draft.skills.isNotEmpty()) throw LlmFailure(LlmFailureKind.FAILED) }
    }

    /** The claim, or null when it is malformed, over-long, HTML or cites an entry that was not sent. A bad claim is dropped, never a failure. */
    private fun evidence(e: JsonElement?, positions: Set<Int>, projects: Set<Int>): AskedEvidence? {
        val o = e as? JsonObject ?: return null
        fun text(key: String) = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val kind = EvidenceKind.entries.firstOrNull { it.name == text("kind")?.trim()?.uppercase() } ?: return null
        val index = (o["index"] as? JsonPrimitive)?.intOrNull ?: return null
        val known = if (kind == EvidenceKind.EXPERIENCE) positions else projects
        val quote = text("quote")?.trim().orEmpty()
        if (index !in known || quote.isEmpty() || quote.length > SkillExtractor.MAX_QUOTE || HTML_TAG.containsMatchIn(quote)) return null
        return AskedEvidence(kind, index, quote)
    }

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val MAX_OUTPUT_TOKENS = 4_000
        val HTML_TAG = Regex("<\\s*[A-Za-z/!]")
    }
}
