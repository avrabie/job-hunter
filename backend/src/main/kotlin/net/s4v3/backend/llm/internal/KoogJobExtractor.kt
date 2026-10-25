package net.s4v3.backend.llm.internal

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.s4v3.backend.llm.JobDraft
import net.s4v3.backend.llm.JobExtractor
import net.s4v3.backend.llm.LetterLanguage
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [JobExtractor] on Koog. One plain-text request (no tools); the answer is a JSON object that is parsed, cleaned and checked.
 * Nothing from the page or the answer is logged.
 */
@Component
internal class KoogJobExtractor(
    private val llm: LlmSettings,
    private val import: JobImportSettings,
    private val executors: ExecutorSource,
) : JobExtractor {

    private val log = LoggerFactory.getLogger(KoogJobExtractor::class.java)

    private val executor: PromptExecutor by lazy { executors.create(llm.apiKey) }

    override fun extract(pageText: String): JobDraft {
        if (llm.apiKey.isBlank()) throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val modelName = import.model.ifBlank { llm.model }
        val model = KoogProjectAnalyst.modelFor(modelName) ?: throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val started = System.nanoTime()
        try {
            val raw = runBlocking {
                withTimeout(import.timeout.toMillis()) {
                    val p = prompt("job-extraction", LLMParams(maxTokens = MAX_OUTPUT_TOKENS)) {
                        system(JobPrompts.SYSTEM)
                        user(JobPrompts.user(pageText))
                    }
                    executor.execute(p, model).textContent()
                }
            }
            val draft = JobDraftCleaner.clean(raw) ?: throw LlmFailure(LlmFailureKind.FAILED)
            log.info("Job extracted by the model in {} ms (found: {})", elapsedMs(started), draft.found)
            return draft
        } catch (e: LlmFailure) {
            log.warn("Job extraction failed after {} ms (model {}): unusable answer", elapsedMs(started), modelName)
            throw e
        } catch (e: Exception) {
            // Only the class name: an exception message may carry the prompt or the answer.
            log.warn(
                "Job extraction failed after {} ms (model {}): {}{}", elapsedMs(started), modelName, e::class.simpleName,
                if (e is LLMClientException) describeClientFailure(e.message) else "",
            )
            throw LlmFailure(LlmFailureKind.FAILED)
        }
    }

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val MAX_OUTPUT_TOKENS = 8_000
    }
}

@Serializable
internal data class JobDraftJson(
    val found: Boolean = false,
    val title: String = "",
    val company: String = "",
    val location: String? = null,
    val description: String = "",
    val language: String = "EN",
)

/** Output cleaning of design decision 5 (the grounding check is the caller's). Returns null when the answer must be refused. */
internal object JobDraftCleaner {
    const val MAX_TITLE_CHARS = 200
    const val MAX_COMPANY_CHARS = 200
    const val MAX_LOCATION_CHARS = 200
    const val MAX_DESCRIPTION_CHARS = 20_000
    private val FENCE = Regex("^```[A-Za-z]*[ \\t]*\\n(.*)\\n```$", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TAG = Regex("<[A-Za-z/!][A-Za-z0-9-]*")
    private val JSON = Json { ignoreUnknownKeys = true }

    fun clean(raw: String): JobDraft? {
        var text = raw.trim()
        FENCE.matchEntire(text)?.let { text = it.groupValues[1].trim() }
        val parsed = try {
            JSON.decodeFromString(JobDraftJson.serializer(), text)
        } catch (_: Exception) {
            return null
        }
        if (!parsed.found) return JobDraft(false, "", "", null, "", LetterLanguage.EN)
        val title = parsed.title.trim()
        val company = parsed.company.trim()
        val location = parsed.location?.trim()?.ifEmpty { null }
        val description = parsed.description.trim()
        if (title.isEmpty() || company.isEmpty() || description.isEmpty()) return null
        if (title.length > MAX_TITLE_CHARS || company.length > MAX_COMPANY_CHARS) return null
        if ((location?.length ?: 0) > MAX_LOCATION_CHARS || description.length > MAX_DESCRIPTION_CHARS) return null
        if (listOfNotNull(title, company, location, description).any { HTML_TAG.containsMatchIn(it) }) return null
        val language = when (parsed.language.trim().uppercase()) {
            "EN" -> LetterLanguage.EN
            "DE" -> LetterLanguage.DE
            else -> return null
        }
        return JobDraft(true, title, company, location, description, language)
    }
}
