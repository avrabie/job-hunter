package net.s4v3.backend.llm.internal

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.s4v3.backend.llm.LetterRequest
import net.s4v3.backend.llm.LetterWriter
import net.s4v3.backend.llm.LlmFailure
import net.s4v3.backend.llm.LlmFailureKind
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [LetterWriter] on Koog. One plain-text request (no structured JSON, no tools); the answer is cleaned and checked. Nothing
 * from the job, the profile or the answer is logged.
 */
@Component
internal class KoogLetterWriter(
    private val llm: LlmSettings,
    private val letters: LettersSettings,
    private val executors: ExecutorSource,
) : LetterWriter {

    private val log = LoggerFactory.getLogger(KoogLetterWriter::class.java)

    private val executor: PromptExecutor by lazy { executors.create(llm.apiKey) }

    override fun write(request: LetterRequest): String {
        if (llm.apiKey.isBlank()) throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val modelName = letters.model.ifBlank { llm.model }
        val model = KoogProjectAnalyst.modelFor(modelName) ?: throw LlmFailure(LlmFailureKind.NOT_CONFIGURED)
        val started = System.nanoTime()
        try {
            val raw = runBlocking {
                withTimeout(letters.timeout.toMillis()) {
                    val p = prompt("motivation-letter", LLMParams(maxTokens = MAX_OUTPUT_TOKENS)) {
                        system(LetterPrompts.SYSTEM)
                        user(LetterPrompts.user(request))
                    }
                    executor.execute(p, model).textContent()
                }
            }
            val text = LetterCleaner.clean(raw) ?: throw LlmFailure(LlmFailureKind.FAILED)
            log.info("Letter written by the model in {} ms ({} characters)", elapsedMs(started), text.length)
            return text
        } catch (e: LlmFailure) {
            log.warn("Letter writing failed after {} ms (model {}): unusable answer", elapsedMs(started), modelName)
            throw e
        } catch (e: Exception) {
            // Only the class name: an exception message may carry the prompt or the answer.
            log.warn(
                "Letter writing failed after {} ms (model {}): {}", elapsedMs(started), modelName, e::class.simpleName)
            throw LlmFailure(LlmFailureKind.FAILED)
        }
    }

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    private companion object {
        const val MAX_OUTPUT_TOKENS = 2_000
    }
}

/** Output cleaning of design decision 5. Returns null when the answer must be refused. */
internal object LetterCleaner {
    const val MAX_CHARS = 6_000
    private val FENCE = Regex("^```[A-Za-z]*[ \\t]*\\n(.*)\\n```$", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TAG = Regex("<[A-Za-z/!][A-Za-z0-9-]*")

    fun clean(raw: String): String? {
        var text = raw.trim()
        FENCE.matchEntire(text)?.let { text = it.groupValues[1].trim() }
        if (text.isEmpty() || text.length > MAX_CHARS) return null
        if (HTML_TAG.containsMatchIn(text)) return null
        return text
    }
}
